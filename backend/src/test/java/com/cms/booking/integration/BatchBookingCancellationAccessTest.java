package com.cms.booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.domain.Booking;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 057-day-sheet-status-overhaul US3 (FR-015, research.md Decision 7): the batch-cancel endpoint
 * is ClinicAdmin/Operations-only - deliberately narrower than the existing single-cancel
 * endpoint, which currently allows any active role (including Doctor).
 */
class BatchBookingCancellationAccessTest extends AbstractBookingCancellationIntegrationTest {

    private ResultActions cancelBatch(String clinicId, String sessionId, String token, String bookingId) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/sessions/{sessionId}/bookings/cancel-batch", clinicId, sessionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"bookingIds\": [\"" + bookingId + "\"] }"));
    }

    @Test
    void doctorCallerIsForbiddenForTheWholeBatch() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Booking booking = saveConfirmedBookingAt(clinic, doctor, LocalTime.now().plusHours(1));
        String token = doctorToken(doctor);

        cancelBatch(clinic.getId().toString(), booking.getSlot().getSession().getId().toString(), token, booking.getId().toString())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void operationsCallerIsAllowed() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Booking booking = saveConfirmedBookingAt(clinic, doctor, LocalTime.now().plusHours(1));
        String token = operationsToken(clinic);

        cancelBatch(clinic.getId().toString(), booking.getSlot().getSession().getId().toString(), token, booking.getId().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled.length()").value(1));
    }

    @Test
    void rejectsAMissingBearerToken() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/clinics/{clinicId}/sessions/{sessionId}/bookings/cancel-batch",
                        java.util.UUID.randomUUID(),
                        java.util.UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"bookingIds\": [] }"))
                .andExpect(status().isUnauthorized());
    }
}
