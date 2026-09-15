package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 025 US1 (P1), FR-005/SC-003: no fee resolvable blocks everything, before any write. */
class WalkInFeeBlockTest extends AbstractWalkInIntegrationTest {

    private ResultActions insert(String clinicId, String sessionId, String token, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/sessions/{sessionId}/walk-in", clinicId, sessionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void noFeeConfiguredBlocksInsertionAndCreatesNothing() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var appointmentType = saveAppointmentTypeWithNoOverride(doctor);
        String token = clinicAdminToken(clinic);

        insert(clinic.getId().toString(), session.getId().toString(), token,
                        """
                        { "patientName": "Walk-in Patient", "appointmentTypeId": "%s" }
                        """.formatted(appointmentType.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NO_FEE_CONFIGURED"));

        assertThat(patientRepository.findAll()).isEmpty();
        assertThat(bookingRepository.findAll()).isEmpty();
    }
}
