package com.cms.booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.scheduling.Session;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 029 US1 (P1), FR-005/SC-004: "already cancelled" is structural - zero currently-active Bookings. */
class SessionCancellationRejectionTest extends AbstractSessionCancellationIntegrationTest {

    private ResultActions cancel(String clinicId, String sessionId, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/sessions/{sessionId}/cancel", clinicId, sessionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void repeatedCancellationAgainstAnAlreadyCancelledSessionIsRejected() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<com.cms.scheduling.Slot> slots = slotRepository.findBySession_Id(session.getId());
        bookSlot(clinic, doctor, slots.get(0));
        String token = clinicAdminToken(clinic);

        cancel(clinic.getId().toString(), session.getId().toString(), token).andExpect(status().isOk());

        cancel(clinic.getId().toString(), session.getId().toString(), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SESSION_ALREADY_CANCELLED"));
    }

    @Test
    void sessionThatNeverHadAnyBookingIsAlsoRejected() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        String token = clinicAdminToken(clinic);

        cancel(clinic.getId().toString(), session.getId().toString(), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SESSION_ALREADY_CANCELLED"));
    }
}
