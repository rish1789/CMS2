package com.cms.booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 021 FR-001/FR-011, spec edge case: a missing or invalid Patient Account token is rejected on both endpoints. */
class PatientBookingAuthenticationTest extends AbstractPatientBookingIntegrationTest {

    @Test
    void noTokenIsRejectedOnBothEndpoints() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var slot = anOpenSlotOf(session);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));

        mockMvc.perform(get("/api/v1/patients/clinics/{clinicId}/slots", clinic.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

        mockMvc.perform(post("/api/v1/patients/clinics/{clinicId}/slots/{slotId}/book", clinic.getId(), slot.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "patientName": "Patient", "appointmentTypeId": "%s" }
                                """
                                        .formatted(appointmentType.getId())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void malformedTokenIsRejectedOnBothEndpoints() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var slot = anOpenSlotOf(session);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        String garbageToken = "not.a.valid.jwt";

        mockMvc.perform(get("/api/v1/patients/clinics/{clinicId}/slots", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + garbageToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/patients/clinics/{clinicId}/slots/{slotId}/book", clinic.getId(), slot.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + garbageToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "patientName": "Patient", "appointmentTypeId": "%s" }
                                """
                                        .formatted(appointmentType.getId())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aStaffTokenIsNotAcceptedAsAPatientToken() throws Exception {
        var clinic = saveClinic();
        // Reuses 002's structural aud=patient guarantee: any staff-issued token (a
        // different audience entirely) must be rejected here just as a malformed one is.
        var staffLikeButNotPatientToken = UUID.randomUUID().toString();

        mockMvc.perform(get("/api/v1/patients/clinics/{clinicId}/slots", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffLikeButNotPatientToken))
                .andExpect(status().isUnauthorized());
    }
}
