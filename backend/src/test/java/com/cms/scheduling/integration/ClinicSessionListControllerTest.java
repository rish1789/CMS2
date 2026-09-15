package com.cms.scheduling.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.scheduling.Schedule;
import com.cms.scheduling.ScheduleMode;
import com.cms.scheduling.Session;
import com.cms.scheduling.SessionGenerationService;
import com.cms.scheduling.SessionRepository;
import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotRepository;
import com.cms.scheduling.SlotStatus;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

/** 041-staff-console-pickers T010/US2: the day sheet's top-level session list. */
class ClinicSessionListControllerTest extends AbstractScheduleIntegrationTest {

    @Autowired
    private SessionGenerationService sessionGenerationService;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SlotRepository slotRepository;

    @AfterEach
    void cleanSessions() {
        sessionRepository.deleteAll();
    }

    private Session generateFixedTimeSession(Clinic clinic, DoctorProfile doctor) {
        Schedule schedule = scheduleRepository.save(new Schedule(
                doctor, clinic, EnumSet.allOf(DayOfWeek.class),
                LocalTime.of(9, 0), LocalTime.of(13, 0), ScheduleMode.FIXED_TIME, 15));
        LocalDate generationDate = LocalDate.now();
        sessionGenerationService.generate(generationDate);
        return sessionRepository.findBySchedule_Id(schedule.getId()).get(0);
    }

    /** 042-day-sheet-hardening: Queue-mode Sessions get no pre-generated Slots (013's own design - slots are created on demand). */
    private Session generateQueueSession(Clinic clinic, DoctorProfile doctor) {
        Schedule schedule = scheduleRepository.save(new Schedule(
                doctor, clinic, EnumSet.allOf(DayOfWeek.class),
                LocalTime.of(9, 0), LocalTime.of(13, 0), ScheduleMode.QUEUE, null));
        sessionGenerationService.generate(LocalDate.now());
        return sessionRepository.findBySchedule_Id(schedule.getId()).get(0);
    }

    @Test
    void listsSessionsWithinTheGivenWindow() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = generateFixedTimeSession(clinic, doctor);

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions", clinic.getId())
                        .param("from", session.getSessionDate().toString())
                        .param("to", session.getSessionDate().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions.length()").value(1))
                .andExpect(jsonPath("$.sessions[0].sessionId").value(session.getId().toString()))
                .andExpect(jsonPath("$.sessions[0].doctorProfileId").value(doctor.getId().toString()))
                .andExpect(jsonPath("$.sessions[0].mode").value("FIXED_TIME"))
                // staff-console-audit-2026-09-10 P1: the Day Sheet list previously showed only a
                // date, never a time range.
                .andExpect(jsonPath("$.sessions[0].startTime").value("09:00:00"))
                .andExpect(jsonPath("$.sessions[0].endTime").value("13:00:00"));
    }

    @Test
    void excludesSessionsOutsideTheWindow() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = generateFixedTimeSession(clinic, doctor);
        LocalDate farFuture = session.getSessionDate().plusDays(100);

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions", clinic.getId())
                        .param("from", farFuture.toString())
                        .param("to", farFuture.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions.length()").value(0));
    }

    @Test
    void rejectsACallerWithNoActiveRoleAtTheClinic() throws Exception {
        Clinic clinic = saveClinic();

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + unrelatedStaffToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void aDoctorOnlyCallerSeesOnlyTheirOwnSessionsWhileClinicAdminSeesEveryDoctors() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctorA = saveDoctorStaffedAt(clinic);
        DoctorProfile doctorB = saveDoctorStaffedAt(clinic);
        // Both schedules cover every day of week and are generated from "now", so both
        // horizons are identical - every date in range has a session for both doctors.
        generateFixedTimeSession(clinic, doctorA);
        generateFixedTimeSession(clinic, doctorB);

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorToken(doctorA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[*].doctorProfileId", org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is(doctorA.getId().toString()))));

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[*].doctorProfileId", org.hamcrest.Matchers.hasItems(
                        doctorA.getId().toString(), doctorB.getId().toString())));
    }

    /** 042-day-sheet-hardening FR-005/SC-002: the list is paginated, not returned in one unbounded response. */
    @Test
    void paginatesResultsAndReportsTotalCount() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctorA = saveDoctorStaffedAt(clinic);
        DoctorProfile doctorB = saveDoctorStaffedAt(clinic);
        DoctorProfile doctorC = saveDoctorStaffedAt(clinic);
        generateFixedTimeSession(clinic, doctorA);
        generateFixedTimeSession(clinic, doctorB);
        generateFixedTimeSession(clinic, doctorC);

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions", clinic.getId())
                        .param("page", "0")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.pageSize").value(2))
                .andExpect(jsonPath("$.totalCount").value(3));

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions", clinic.getId())
                        .param("page", "1")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions.length()").value(1))
                .andExpect(jsonPath("$.totalCount").value(3));
    }

    /** 042-day-sheet-hardening FR-004: filters the list down to one doctor. */
    @Test
    void filtersToOneDoctor() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctorA = saveDoctorStaffedAt(clinic);
        DoctorProfile doctorB = saveDoctorStaffedAt(clinic);
        generateFixedTimeSession(clinic, doctorA);
        generateFixedTimeSession(clinic, doctorB);

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions", clinic.getId())
                        .param("doctorProfileId", doctorA.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions.length()").value(1))
                .andExpect(jsonPath("$.sessions[0].doctorProfileId").value(doctorA.getId().toString()));
    }

    /** 042-day-sheet-hardening FR-004/data-model.md: the doctors list is complete regardless of the current page. */
    @Test
    void doctorsListIsCompleteRegardlessOfCurrentPage() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctorA = saveDoctorStaffedAt(clinic);
        DoctorProfile doctorB = saveDoctorStaffedAt(clinic);
        DoctorProfile doctorC = saveDoctorStaffedAt(clinic);
        generateFixedTimeSession(clinic, doctorA);
        generateFixedTimeSession(clinic, doctorB);
        generateFixedTimeSession(clinic, doctorC);

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions", clinic.getId())
                        .param("page", "0")
                        .param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions.length()").value(1))
                .andExpect(jsonPath("$.doctors.length()").value(3))
                .andExpect(jsonPath("$.doctors[*].doctorProfileId", org.hamcrest.Matchers.containsInAnyOrder(
                        doctorA.getId().toString(), doctorB.getId().toString(), doctorC.getId().toString())))
                // Lets the frontend search by Staff ID as well as name (mirrors Roster's precedent).
                .andExpect(jsonPath("$.doctors[*].staffCode", org.hamcrest.Matchers.containsInAnyOrder(
                        doctorA.getAccount().getStaffCode(),
                        doctorB.getAccount().getStaffCode(),
                        doctorC.getAccount().getStaffCode())));
    }

    /**
     * 042-day-sheet-hardening FR-006 regression (speckit-analyze finding E1): a Doctor-only
     * caller filtering by a *different* doctor's id must get zero sessions back on the main
     * results, not that other doctor's sessions - self-scoping must hold under the new
     * doctorProfileId param, not just on the separate doctors[] list.
     */
    @Test
    void doctorOnlyCallerFilteringByAnotherDoctorGetsNoSessions() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctorA = saveDoctorStaffedAt(clinic);
        DoctorProfile doctorB = saveDoctorStaffedAt(clinic);
        generateFixedTimeSession(clinic, doctorA);
        generateFixedTimeSession(clinic, doctorB);

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions", clinic.getId())
                        .param("doctorProfileId", doctorB.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorToken(doctorA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions.length()").value(0));
    }

    /** 042-day-sheet-hardening FR-011: booked/total slot counts match the actual Slot data. */
    @Test
    void reportsBookedAndTotalSlotCountsMatchingActualSlotData() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = generateFixedTimeSession(clinic, doctor);
        List<Slot> slots = slotRepository.findBySession_Id(session.getId());
        org.junit.jupiter.api.Assertions.assertFalse(slots.isEmpty());

        Slot firstSlot = slots.get(0);
        firstSlot.setStatus(SlotStatus.BOOKED);
        slotRepository.save(firstSlot);

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions", clinic.getId())
                        .param("doctorProfileId", doctor.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].totalSlotCount").value(slots.size()))
                .andExpect(jsonPath("$.sessions[0].bookedSlotCount").value(1));
    }

    /** 042-day-sheet-hardening FR-011/Edge Cases: a session with no generated slots yet reports 0/0, not an error. */
    @Test
    void reportsZeroZeroForASessionWithNoSlotsYet() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = generateQueueSession(clinic, doctor);
        org.junit.jupiter.api.Assertions.assertTrue(slotRepository.findBySession_Id(session.getId()).isEmpty());

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions", clinic.getId())
                        .param("doctorProfileId", doctor.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].totalSlotCount").value(0))
                .andExpect(jsonPath("$.sessions[0].bookedSlotCount").value(0));
    }
}
