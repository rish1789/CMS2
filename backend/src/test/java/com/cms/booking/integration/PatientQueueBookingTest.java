package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.patient.record.Patient;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 022 FR-002/FR-003/FR-007, spec US2: patient self-service queue booking. */
class PatientQueueBookingTest extends AbstractQueueBookingIntegrationTest {

    @Test
    void bookingAsAnAlreadyLinkedPatientSucceedsAndAssignsAToken() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveQueueSession(clinic, doctor);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var patientAccount = savePatientAccount();
        String token = patientToken(patientAccount);

        Patient linked = patientRepository.save(new Patient(clinic, null, "Linked Patient", null));
        linked.setPatientAccount(patientAccount);
        patientRepository.save(linked);

        mockMvc.perform(post(
                                "/api/v1/patients/clinics/{clinicId}/sessions/{sessionId}/queue-bookings",
                                clinic.getId(),
                                session.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "patientName": "Linked Patient", "appointmentTypeId": "%s" }
                                """
                                        .formatted(appointmentType.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenNumber").value(1))
                .andExpect(jsonPath("$.patientId").value(linked.getId().toString()));

        assertThat(patientRepository.findAll()).hasSize(1);
    }

    @Test
    void firstBookingWithNoMatchCreatesAndLinksANewPatientRecord() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveQueueSession(clinic, doctor);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var patientAccount = savePatientAccount();
        String token = patientToken(patientAccount);

        mockMvc.perform(post(
                                "/api/v1/patients/clinics/{clinicId}/sessions/{sessionId}/queue-bookings",
                                clinic.getId(),
                                session.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "patientName": "Brand New Patient", "appointmentTypeId": "%s" }
                                """
                                        .formatted(appointmentType.getId())))
                .andExpect(status().isCreated());

        assertThat(patientRepository.findAll()).hasSize(1);
        var created = patientRepository.findAll().get(0);
        assertThat(created.getPatientAccount().getId()).isEqualTo(patientAccount.getId());
    }

    @Test
    void feeBlockUnauthenticatedAndNonQueueSessionAreAllRejected() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var appointmentType = saveAppointmentTypeWithNoOverride(doctor);
        var patientAccount = savePatientAccount();
        String token = patientToken(patientAccount);

        var session = saveQueueSession(clinic, doctor);
        mockMvc.perform(post(
                                "/api/v1/patients/clinics/{clinicId}/sessions/{sessionId}/queue-bookings",
                                clinic.getId(),
                                session.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "patientName": "Patient", "appointmentTypeId": "%s" }
                                """
                                        .formatted(appointmentType.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NO_FEE_CONFIGURED"));
        assertThat(patientRepository.findAll()).isEmpty();

        var appointmentTypeWithFee = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        mockMvc.perform(post(
                                "/api/v1/patients/clinics/{clinicId}/sessions/{sessionId}/queue-bookings",
                                clinic.getId(),
                                session.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "patientName": "Patient", "appointmentTypeId": "%s" }
                                """
                                        .formatted(appointmentTypeWithFee.getId())))
                .andExpect(status().isUnauthorized());

        var fixedTimeSession = saveFixedTimeSession(clinic, doctor);
        mockMvc.perform(post(
                                "/api/v1/patients/clinics/{clinicId}/sessions/{sessionId}/queue-bookings",
                                clinic.getId(),
                                fixedTimeSession.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "patientName": "Patient", "appointmentTypeId": "%s" }
                                """
                                        .formatted(appointmentTypeWithFee.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NOT_A_QUEUE_SESSION"));
    }
}
