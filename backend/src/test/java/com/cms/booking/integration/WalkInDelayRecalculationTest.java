package com.cms.booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 026 US2 (P2), FR-003: a walk-in insertion into a Fixed-Time Session (025) is the second of
 * exactly two delay-recalculation trigger points. Lives alongside 025's own tests since it
 * exercises {@code WalkInInsertionService} directly, per tasks.md.
 */
class WalkInDelayRecalculationTest extends AbstractWalkInIntegrationTest {

    private ResultActions insert(String clinicId, String sessionId, String token, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/sessions/{sessionId}/walk-in", clinicId, sessionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions delay(String clinicId, String sessionId, String token) throws Exception {
        return mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions/{sessionId}/delay", clinicId, sessionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void walkInInsertionRecalculatesDelayForTheStillOutstandingEarlierSlot() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        // Generated for a fixed past date (2026-09-03) - every Slot's scheduled time is
        // already behind "now" by construction, so any still-OPEN regular Slot left over after
        // the walk-in books the buffer Slot qualifies as an outstanding past-due Slot.
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        String token = clinicAdminToken(clinic);
        String clinicId = clinic.getId().toString();
        String sessionId = session.getId().toString();

        delay(clinicId, sessionId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicable").value(true))
                .andExpect(jsonPath("$.delayMinutes").doesNotExist());

        insert(clinicId, sessionId, token,
                        """
                        { "patientName": "Walk-in Patient", "appointmentTypeId": "%s" }
                        """.formatted(appointmentType.getId()))
                .andExpect(status().isCreated());

        delay(clinicId, sessionId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicable").value(true))
                .andExpect(jsonPath("$.delayMinutes").value(org.hamcrest.Matchers.greaterThan(0)));
    }
}
