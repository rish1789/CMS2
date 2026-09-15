package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 022 FR-001/FR-003/FR-005/FR-006/FR-009/FR-010, spec US1: staff-assisted queue booking. */
class StaffQueueBookingTest extends AbstractQueueBookingIntegrationTest {

    @Test
    void bookingAnExistingPatientIntoAQueueSessionSucceedsAndAssignsAToken() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveQueueSession(clinic, doctor);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var patient = saveExistingPatient(clinic);
        String token = clinicAdminToken(clinic);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/sessions/{sessionId}/queue-bookings", clinic.getId(), session.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "patientId": "%s", "appointmentTypeId": "%s" }
                                """
                                        .formatted(patient.getId(), appointmentType.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lockedFee").value(300.00))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.patientId").value(patient.getId().toString()))
                .andExpect(jsonPath("$.tokenNumber").value(1));

        assertThat(bookingRepository.findAll()).hasSize(1);
        assertThat(slotRepository.findAll()).hasSize(1);
        assertThat(slotRepository.findAll().get(0).getTokenNumber()).isEqualTo(1);
    }

    @Test
    void bookingAWalkInWithNoExistingPatientCreatesOneAsPartOfTheBooking() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveQueueSession(clinic, doctor);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        String token = clinicAdminToken(clinic);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/sessions/{sessionId}/queue-bookings", clinic.getId(), session.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "patientName": "Walk-in Patient", "patientPhone": "9876543210", "appointmentTypeId": "%s" }
                                """
                                        .formatted(appointmentType.getId())))
                .andExpect(status().isCreated());

        assertThat(patientRepository.findAll()).hasSize(1);
        assertThat(patientRepository.findAll().get(0).getName()).isEqualTo("Walk-in Patient");
    }

    @Test
    void noFeeConfiguredBlocksTheBookingAndCreatesNothing() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveQueueSession(clinic, doctor);
        var appointmentType = saveAppointmentTypeWithNoOverride(doctor);
        var patient = saveExistingPatient(clinic);
        String token = clinicAdminToken(clinic);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/sessions/{sessionId}/queue-bookings", clinic.getId(), session.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "patientId": "%s", "appointmentTypeId": "%s" }
                                """
                                        .formatted(patient.getId(), appointmentType.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NO_FEE_CONFIGURED"));

        assertThat(bookingRepository.findAll()).isEmpty();
        assertThat(slotRepository.findAll()).isEmpty();
    }

    @Test
    void operationsAndClinicAdminSucceedDoctorAndUnrelatedStaffAreForbiddenNoTokenIsUnauthorized() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));

        var sessionForOps = saveQueueSession(clinic, doctor);
        var patientA = saveExistingPatient(clinic);
        book(clinic.getId().toString(), sessionForOps.getId().toString(), operationsToken(clinic), patientA.getId().toString(), appointmentType.getId().toString())
                .andExpect(status().isCreated());

        var sessionForAdmin = saveQueueSession(clinic, doctor);
        var patientB = saveExistingPatient(clinic);
        book(clinic.getId().toString(), sessionForAdmin.getId().toString(), clinicAdminToken(clinic), patientB.getId().toString(), appointmentType.getId().toString())
                .andExpect(status().isCreated());

        var sessionForDoctor = saveQueueSession(clinic, doctor);
        var patientC = saveExistingPatient(clinic);
        book(clinic.getId().toString(), sessionForDoctor.getId().toString(), doctorToken(doctor), patientC.getId().toString(), appointmentType.getId().toString())
                .andExpect(status().isForbidden());

        var sessionForUnrelated = saveQueueSession(clinic, doctor);
        var patientD = saveExistingPatient(clinic);
        book(clinic.getId().toString(), sessionForUnrelated.getId().toString(), unrelatedStaffToken(), patientD.getId().toString(), appointmentType.getId().toString())
                .andExpect(status().isForbidden());

        var sessionNoToken = saveQueueSession(clinic, doctor);
        mockMvc.perform(post("/api/v1/clinics/{clinicId}/sessions/{sessionId}/queue-bookings", clinic.getId(), sessionNoToken.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "patientId": "%s", "appointmentTypeId": "%s" }
                                """
                                        .formatted(patientA.getId(), appointmentType.getId())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonexistentOrWrongClinicSessionIsNotFoundNonQueueSessionIsRejectedMismatchedAppointmentTypeIsNotFound() throws Exception {
        var clinicA = saveClinic();
        var clinicB = saveClinic();
        var doctor = saveDoctorStaffedAt(clinicA);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var patient = saveExistingPatient(clinicA);
        String token = clinicAdminToken(clinicA);

        book(clinicA.getId().toString(), java.util.UUID.randomUUID().toString(), token, patient.getId().toString(), appointmentType.getId().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SESSION_NOT_FOUND"));

        var session = saveQueueSession(clinicA, doctor);
        book(clinicB.getId().toString(), session.getId().toString(), clinicAdminToken(clinicB), patient.getId().toString(), appointmentType.getId().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SESSION_NOT_FOUND"));

        var fixedTimeSession = saveFixedTimeSession(clinicA, doctor);
        book(clinicA.getId().toString(), fixedTimeSession.getId().toString(), token, patient.getId().toString(), appointmentType.getId().toString())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NOT_A_QUEUE_SESSION"));

        var otherDoctor = saveDoctorStaffedAt(clinicA);
        var otherDoctorsAppointmentType = saveAppointmentTypeWithOverride(otherDoctor, new BigDecimal("300.00"));
        var queueSession = saveQueueSession(clinicA, doctor);
        book(clinicA.getId().toString(), queueSession.getId().toString(), token, patient.getId().toString(), otherDoctorsAppointmentType.getId().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("APPOINTMENT_TYPE_NOT_FOUND"));
    }

    private org.springframework.test.web.servlet.ResultActions book(
            String clinicId, String sessionId, String token, String patientId, String appointmentTypeId) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/sessions/{sessionId}/queue-bookings", clinicId, sessionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                        { "patientId": "%s", "appointmentTypeId": "%s" }
                        """
                                .formatted(patientId, appointmentTypeId)));
    }
}
