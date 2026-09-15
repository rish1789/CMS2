package com.cms.scheduling.integration;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 013 FR-010, spec US1 AC1: GET lists exactly the schedules created for that doctor/clinic pair. */
class ListSchedulesTest extends AbstractScheduleIntegrationTest {

    @Test
    void listsExactlyTheSchedulesForThisDoctorAndClinic() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var otherDoctor = saveDoctorStaffedAt(clinic);
        String token = clinicAdminToken(clinic);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/doctors/{doctorId}/schedules", clinic.getId(), doctor.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                        { "daysOfWeek": ["MONDAY"], "startTime": "09:00", "endTime": "10:00", "mode": "QUEUE" }
                        """));
        mockMvc.perform(post("/api/v1/clinics/{clinicId}/doctors/{doctorId}/schedules", clinic.getId(), doctor.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                        { "daysOfWeek": ["TUESDAY"], "startTime": "11:00", "endTime": "12:00", "mode": "QUEUE" }
                        """));
        mockMvc.perform(post("/api/v1/clinics/{clinicId}/doctors/{doctorId}/schedules", clinic.getId(), otherDoctor.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                        { "daysOfWeek": ["WEDNESDAY"], "startTime": "09:00", "endTime": "10:00", "mode": "QUEUE" }
                        """));

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/doctors/{doctorId}/schedules", clinic.getId(), doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    /**
     * Regression test: {@code daysOfWeek} is an {@code @ElementCollection} (LAZY by JPA
     * default) - {@code ScheduleService.list()}'s {@code @Transactional} boundary closes
     * before {@code ScheduleController} maps the returned entities to {@code
     * ScheduleResponse} (open-in-view: false), so a lazy collection here throws {@code
     * LazyInitializationException} on every call, serialized as an unhandled 500 before
     * this fix (Schedule.daysOfWeek now EAGER). Saves directly via the repository (not the
     * POST endpoint) so this test exercises a Schedule genuinely loaded fresh from the
     * database by GET's own query, not an in-memory instance from an earlier call.
     */
    @Test
    void listReturnsAScheduleFreshFromTheDatabaseWithDaysOfWeekPopulated() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        scheduleRepository.save(new com.cms.scheduling.Schedule(
                doctor,
                clinic,
                java.util.Set.of(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.FRIDAY),
                java.time.LocalTime.of(9, 0),
                java.time.LocalTime.of(13, 0),
                com.cms.scheduling.ScheduleMode.FIXED_TIME,
                15));
        String token = clinicAdminToken(clinic);

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/doctors/{doctorId}/schedules", clinic.getId(), doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].daysOfWeek", hasSize(3)))
                .andExpect(jsonPath("$[0].daysOfWeek", containsInAnyOrder("MONDAY", "WEDNESDAY", "FRIDAY")));
    }
}
