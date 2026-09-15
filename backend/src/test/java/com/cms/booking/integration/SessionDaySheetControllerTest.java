package com.cms.booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.AppointmentType;
import com.cms.booking.StaffBookingService;
import com.cms.identity.account.Account;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.record.Patient;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** 041-staff-console-pickers T011/US2: the day sheet's per-session slot+booking detail. */
class SessionDaySheetControllerTest extends AbstractStaffBookingIntegrationTest {

    private UUID saveClinicAdminAccountId(Clinic clinic) {
        Account admin = accountRepository.save(new Account(
                "Admin", "admin-" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"), "CA-" + UUID.randomUUID(), null));
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
