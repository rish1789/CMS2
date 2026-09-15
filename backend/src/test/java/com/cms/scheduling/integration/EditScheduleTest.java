package com.cms.scheduling.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.scheduling.ScheduleSessionGenerator;
import com.cms.scheduling.Session;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 016 FR-001..FR-008: editing a Schedule re-validates/re-checks-overlap/authorizes like create, and never touches Sessions. */
class EditScheduleTest extends AbstractSessionGenerationIntegrationTest {

    @Autowired
    private ScheduleSessionGenerator scheduleSessionGenerator;

    private ResultActions edit(String clinicId, String doctorId, String scheduleId, String token, String body)
            throws Exception {
        return mockMvc.perform(patch(
                        "/api/v1/clinics/{clinicId}/doctors/{doctorId}/schedules/{scheduleId}",
                        clinicId,
                        doctorId,
                        scheduleId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void editingTimeWindowLeavesAlreadyGeneratedSessionsUnchangedAndFutureGenerationUsesTheNewWindow() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var schedule = saveFixedTimeSchedule(clinic, doctor); // 9:00-13:00, Mon/Wed/Fri, 15-min
        LocalDate runDate = LocalDate.of(2026, 9, 3);
        sessionGenerationService.generate(runDate);
        List<Session> before = sessionRepository.findBySchedule_Id(schedule.getId());
        assertThat(before).isNotEmpty();

        String token = clinicAdminToken(clinic);
        edit(
                        clinic.getId().toString(),
                        doctor.getId().toString(),
                        schedule.getId().toString(),
                        token,
                        """
                        { "daysOfWeek": ["MONDAY", "WEDNESDAY", "FRIDAY"], "startTime": "10:00", "endTime": "14:00", "mode": "FIXED_TIME", "slotIntervalMinutes": 20 }
                        """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").value("10:00:00"));

        List<Session> after = sessionRepository.findBySchedule_Id(schedule.getId());
        assertThat(after).hasSameSizeAs(before);
        assertThat(after).allSatisfy(s -> {
            assertThat(s.getStartTime()).isEqualTo(before.get(0).getStartTime());
            assertThat(s.getSlotIntervalMinutes()).isEqualTo(15);
        });

        int createdOnLaterRun = scheduleSessionGenerator.generateForSchedule(schedule.getId(), runDate.plusDays(20));
        assertThat(createdOnLaterRun).isGreaterThan(0);
        List<Session> newest = sessionRepository.findBySchedule_Id(schedule.getId());
        assertThat(newest)
                .filteredOn(s -> s.getSessionDate().isAfter(runDate.plusDays(14)))
                .allSatisfy(s -> assertThat(s.getStartTime().toString()).isEqualTo("10:00"));
    }

    private com.cms.scheduling.Schedule reloadSchedule(UUID scheduleId) {
        return scheduleRepository.findById(scheduleId).orElseThrow();
    }

    @Test
    void editRejectedByValidationLeavesTheScheduleUnchanged() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var schedule = saveFixedTimeSchedule(clinic, doctor);
        String token = clinicAdminToken(clinic);

        edit(
                        clinic.getId().toString(),
                        doctor.getId().toString(),
                        schedule.getId().toString(),
                        token,
                        """
                        { "daysOfWeek": ["MONDAY"], "startTime": "09:00", "endTime": "10:00", "mode": "QUEUE", "slotIntervalMinutes": 10 }
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_SCHEDULE"));

        var reloaded = reloadSchedule(schedule.getId());
        assertThat(reloaded.getMode()).isEqualTo(com.cms.scheduling.ScheduleMode.FIXED_TIME);
        assertThat(reloaded.getStartTime().toString()).isEqualTo("09:00");
    }

    @Test
    void editCreatingANewOverlapIsRejectedButEditingOnlyOwnFieldsIsNot() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        String token = clinicAdminToken(clinic);

        // Schedule A: Mon 9-11
        var scheduleA = scheduleRepository.save(new com.cms.scheduling.Schedule(
                doctor, clinic, java.util.Set.of(java.time.DayOfWeek.MONDAY),
                java.time.LocalTime.of(9, 0), java.time.LocalTime.of(11, 0),
                com.cms.scheduling.ScheduleMode.QUEUE, null));
        // Schedule B: Mon 12-13 (no overlap with A initially)
        var scheduleB = scheduleRepository.save(new com.cms.scheduling.Schedule(
                doctor, clinic, java.util.Set.of(java.time.DayOfWeek.MONDAY),
                java.time.LocalTime.of(12, 0), java.time.LocalTime.of(13, 0),
                com.cms.scheduling.ScheduleMode.QUEUE, null));

        // Edit B to now overlap A -> rejected
        edit(
                        clinic.getId().toString(),
                        doctor.getId().toString(),
                        scheduleB.getId().toString(),
                        token,
                        """
                        { "daysOfWeek": ["MONDAY"], "startTime": "10:00", "endTime": "13:00", "mode": "QUEUE" }
                        """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SCHEDULE_OVERLAP"));

        // Edit B changing only its own window, no new overlap with A -> accepted, not rejected as "overlapping itself"
        edit(
                        clinic.getId().toString(),
                        doctor.getId().toString(),
                        scheduleB.getId().toString(),
                        token,
                        """
                        { "daysOfWeek": ["MONDAY"], "startTime": "12:30", "endTime": "13:30", "mode": "QUEUE" }
                        """)
                .andExpect(status().isOk());
    }

    @Test
    void editAuthorizationMatchesCreateRule() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var schedule = saveFixedTimeSchedule(clinic, doctor);
        String validBody =
                """
                { "daysOfWeek": ["MONDAY"], "startTime": "09:00", "endTime": "10:00", "mode": "QUEUE" }
                """;

        edit(
                        clinic.getId().toString(),
                        doctor.getId().toString(),
                        schedule.getId().toString(),
                        unrelatedStaffToken(),
                        validBody)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        edit(clinic.getId().toString(), doctor.getId().toString(), schedule.getId().toString(), doctorToken(doctor), validBody)
                .andExpect(status().isOk());
    }

    @Test
    void unknownOrMismatchedScheduleIdIsNotFound() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var otherClinic = saveClinic();
        var otherDoctor = saveDoctorStaffedAt(otherClinic);
        var scheduleAtOtherClinic = saveFixedTimeSchedule(otherClinic, otherDoctor);
        String token = clinicAdminToken(clinic);
        String validBody =
                """
                { "daysOfWeek": ["MONDAY"], "startTime": "09:00", "endTime": "10:00", "mode": "QUEUE" }
                """;

        edit(clinic.getId().toString(), doctor.getId().toString(), UUID.randomUUID().toString(), token, validBody)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SCHEDULE_NOT_FOUND"));

        // A real schedule id, but belonging to a different clinic/doctor than named in the path.
        edit(
                        clinic.getId().toString(),
                        doctor.getId().toString(),
                        scheduleAtOtherClinic.getId().toString(),
                        token,
                        validBody)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SCHEDULE_NOT_FOUND"));
    }
}
