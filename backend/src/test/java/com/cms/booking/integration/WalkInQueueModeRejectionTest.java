package com.cms.booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 025 convergence (F1): walk-in priority insertion is Fixed-Time-only (spec Assumptions/Edge
 * Cases) - a Queue-mode Session must be rejected outright, even when it happens to have a
 * Slot that would otherwise match the tier-3 "any other OPEN regular Slot" candidate.
 */
class WalkInQueueModeRejectionTest extends AbstractWalkInIntegrationTest {

    private ResultActions insert(String clinicId, String sessionId, String token, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/sessions/{sessionId}/walk-in", clinicId, sessionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void queueModeSessionIsRejectedEvenWithAnOtherwiseEligibleOpenSlot() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveQueueSession(clinic, doctor);
        // Mints an OPEN, unbooked Slot (isBuffer=false) - the exact narrow-window shape that
        // would otherwise incorrectly qualify as a tier-3 candidate without the mode guard.
        queueSlotService.issueNextSlot(session.getId());
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        String token = clinicAdminToken(clinic);

        insert(clinic.getId().toString(), session.getId().toString(), token,
                        """
                        { "patientName": "Walk-in Patient", "appointmentTypeId": "%s", "overrideReason": "Anything" }
                        """.formatted(appointmentType.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NOT_A_FIXED_TIME_SESSION"));
    }
}
