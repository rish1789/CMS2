package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.service.StaffBookingService;
import com.cms.identity.account.domain.Account;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.record.domain.Patient;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

/** 041-staff-console-pickers T011/US2: the day sheet's per-session slot+booking detail. */
class SessionDaySheetControllerTest extends AbstractStaffBookingIntegrationTest {

    private UUID saveClinicAdminAccountId(Clinic clinic) {
        Account admin = accountRepository.save(new Account(
                "Admin", "admin-" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"), "CA-" + UUID.randomUUID().toString().substring(0, 13), null));
        roleAssignmentRepository.save(new RoleAssignment(admin, clinic, RoleAssignment.Role.ClinicAdmin));
        return admin.getId();
    }

    @Test
    void showsBookedSlotWithPatientNameAndOpenSlotWithNoBooking() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slotToBook = anOpenSlotOf(session);
        Patient patient = saveExistingPatient(clinic);
        AppointmentType appointmentType = saveAppointmentTypeWithNoOverride(doctor);
        UUID adminAccountId = saveClinicAdminAccountId(clinic);

        staffBookingService.bookSlot(
                adminAccountId,
                clinic.getId(),
                slotToBook.getId(),
                new StaffBookingService.BookSlotInput(
                        patient.getId(), null, null, appointmentType.getId()));

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions/{sessionId}/day-sheet", clinic.getId(), session.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffJwtService.issueToken(adminAccountId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(session.getId().toString()))
                .andExpect(jsonPath("$.doctorProfileId").value(doctor.getId().toString()))
                // 042-day-sheet-hardening FR-010: previously missing - the frontend had no
                // source for these except React Router navigation state, lost on a direct visit.
                .andExpect(jsonPath("$.doctorName").value(doctor.getAccount().getName()))
                .andExpect(jsonPath("$.sessionDate").value(session.getSessionDate().toString()))
                .andExpect(jsonPath("$.mode").value("FIXED_TIME"))
                .andExpect(jsonPath(
                        "$.slots[?(@.slotId=='" + slotToBook.getId() + "')].booking.patientName")
                        .value(patient.getName()))
                .andExpect(jsonPath(
                        "$.slots[?(@.status=='OPEN')][0].booking")
                        .doesNotExist());
    }

    /**
     * day-sheet-slot-ordering-fix: repro for the ordering bug - a slot whose status is
     * changed well after initial generation (the 023 no-show sweep's own shape: fetch, mutate,
     * save one candidate at a time, outside any batch that would preserve original row order)
     * must still come back chronologically. This doesn't rely on the no-show sweep's real grace
     * -period timing, which the test can't fast-forward - flipping status via the repository
     * directly is exactly the same kind of out-of-band UPDATE.
     */
    @Test
    void keepsSlotsInStartTimeOrderAfterAnOutOfBandStatusChange() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<Slot> slotsInGenerationOrder = slotRepository.findBySession_Id(session.getId());
        Slot middleSlot = slotsInGenerationOrder.get(slotsInGenerationOrder.size() / 2);
        Patient patient = saveExistingPatient(clinic);
        AppointmentType appointmentType = saveAppointmentTypeWithNoOverride(doctor);
        UUID adminAccountId = saveClinicAdminAccountId(clinic);

        staffBookingService.bookSlot(
                adminAccountId,
                clinic.getId(),
                middleSlot.getId(),
                new StaffBookingService.BookSlotInput(patient.getId(), null, null, appointmentType.getId()));

        Slot bookedSlot = slotRepository.findById(middleSlot.getId()).orElseThrow();
        bookedSlot.setStatus(SlotStatus.NO_SHOW);
        slotRepository.save(bookedSlot);

        MvcResult result = mockMvc.perform(get(
                        "/api/v1/clinics/{clinicId}/sessions/{sessionId}/day-sheet",
                        clinic.getId(),
                        session.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffJwtService.issueToken(adminAccountId)))
                .andExpect(status().isOk())
                .andReturn();

        List<String> startTimesInResponseOrder =
                JsonPath.read(result.getResponse().getContentAsString(), "$.slots[*].startTime");
        List<String> chronological = startTimesInResponseOrder.stream().sorted().toList();
        assertThat(startTimesInResponseOrder).containsExactlyElementsOf(chronological);
    }

    /**
     * day-sheet-slot-ordering-fix: a second, narrower repro - a single ordinary booking (no
     * no-show/status mutation afterward) is itself an UPDATE on the Slot row (OPEN -&gt;
     * BOOKED, {@link StaffBookingService}), so it hits the exact same "unordered scan"
     * root cause as {@link #keepsSlotsInStartTimeOrderAfterAnOutOfBandStatusChange}, not just
     * background-job-driven transitions.
     */
    @Test
    void keepsSlotsInStartTimeOrderAfterAPlainFreshBooking() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<Slot> slotsInGenerationOrder = slotRepository.findBySession_Id(session.getId());
        Slot middleSlot = slotsInGenerationOrder.get(slotsInGenerationOrder.size() / 2);
        Patient patient = saveExistingPatient(clinic);
        AppointmentType appointmentType = saveAppointmentTypeWithNoOverride(doctor);
        UUID adminAccountId = saveClinicAdminAccountId(clinic);

        staffBookingService.bookSlot(
                adminAccountId,
                clinic.getId(),
                middleSlot.getId(),
                new StaffBookingService.BookSlotInput(patient.getId(), null, null, appointmentType.getId()));

        MvcResult result = mockMvc.perform(get(
                        "/api/v1/clinics/{clinicId}/sessions/{sessionId}/day-sheet",
                        clinic.getId(),
                        session.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffJwtService.issueToken(adminAccountId)))
                .andExpect(status().isOk())
                .andReturn();

        List<String> startTimesInResponseOrder =
                JsonPath.read(result.getResponse().getContentAsString(), "$.slots[*].startTime");
        List<String> chronological = startTimesInResponseOrder.stream().sorted().toList();
        assertThat(startTimesInResponseOrder).containsExactlyElementsOf(chronological);
    }

    @Test
    void rejects404ForASessionNotBelongingToTheGivenClinic() throws Exception {
        Clinic clinicA = saveClinic();
        Clinic clinicB = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinicA);
        Session session = saveFixedTimeSessionWithSlots(clinicA, doctor);

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions/{sessionId}/day-sheet", clinicB.getId(), session.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinicB)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsACallerWithNoActiveRoleAtTheClinic() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions/{sessionId}/day-sheet", clinic.getId(), session.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + unrelatedStaffToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void aDoctorCanViewTheirOwnSessionsDaySheet() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions/{sessionId}/day-sheet", clinic.getId(), session.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorToken(doctor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(session.getId().toString()));
    }

    @Test
    void aDoctorGets404ForAnotherDoctorsSessionAtTheSameClinic() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctorA = saveDoctorStaffedAt(clinic);
        DoctorProfile doctorB = saveDoctorStaffedAt(clinic);
        Session sessionForDoctorB = saveFixedTimeSessionWithSlots(clinic, doctorB);

        mockMvc.perform(get(
                        "/api/v1/clinics/{clinicId}/sessions/{sessionId}/day-sheet",
                        clinic.getId(),
                        sessionForDoctorB.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorToken(doctorA)))
                .andExpect(status().isNotFound());
    }
}
