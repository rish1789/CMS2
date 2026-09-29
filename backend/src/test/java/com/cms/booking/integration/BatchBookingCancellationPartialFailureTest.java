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
 * 057-day-sheet-status-overhaul US3 (FR-014): a booking that can no longer be cancelled by the
 * time the batch is processed (here, already cancelled by another actor) is reported as its own
 * per-booking failure without blocking the rest of the batch from succeeding.
 */
class BatchBookingCancellationPartialFailureTest extends AbstractBookingCancellationIntegrationTest {

    private ResultActions cancelBatch(String clinicId, String sessionId, String token, String... bookingIds) throws Exception {
        String idsJson = String.join(",", java.util.Arrays.stream(bookingIds).map(id -> "\"" + id + "\"").toArray(String[]::new));
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/sessions/{sessionId}/bookings/cancel-batch", clinicId, sessionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"bookingIds\": [" + idsJson + "] }"));
    }

    @Test
    void anAlreadyCancelledBookingInTheBatchIsReportedFailedWithoutBlockingTheOtherOne() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Booking stillCancellable = saveConfirmedBookingAt(clinic, doctor, LocalTime.now().plusHours(1));
        Booking alreadyCancelled = saveConfirmedBookingAt(clinic, doctor, LocalTime.now().plusHours(2));
        String token = clinicAdminToken(clinic);

        // Cancel one of the two ahead of time, via the existing single-cancel endpoint, so the
        // batch call below finds it already in a non-cancellable state.
        mockMvc.perform(post("/api/v1/clinics/{clinicId}/bookings/{bookingId}/cancel", clinic.getId(), alreadyCancelled.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        cancelBatch(
                        clinic.getId().toString(),
                        stillCancellable.getSlot().getSession().getId().toString(),
                        token,
                        stillCancellable.getId().toString(),
                        alreadyCancelled.getId().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled.length()").value(1))
                .andExpect(jsonPath("$.cancelled[0]").value(stillCancellable.getId().toString()))
                .andExpect(jsonPath("$.failed.length()").value(1))
                .andExpect(jsonPath("$.failed[0].bookingId").value(alreadyCancelled.getId().toString()))
                .andExpect(jsonPath("$.failed[0].reason").value("BOOKING_NOT_CANCELLABLE"));
    }
}
