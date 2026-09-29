package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingStatus;
import com.cms.scheduling.domain.SlotStatus;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 057-day-sheet-status-overhaul US3 (FR-012): cancelling several selected bookings in one
 * request succeeds for all, applying the exact same effects (slot released, booking cancelled,
 * BookingCancelledEvent published - which is what a waitlist offer already listens for) as
 * today's single-booking cancellation, per booking.
 */
class BatchBookingCancellationSuccessTest extends AbstractBookingCancellationIntegrationTest {

    private ResultActions cancelBatch(String clinicId, String sessionId, String token, String... bookingIds) throws Exception {
        String idsJson = String.join(",", java.util.Arrays.stream(bookingIds).map(id -> "\"" + id + "\"").toArray(String[]::new));
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/sessions/{sessionId}/bookings/cancel-batch", clinicId, sessionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"bookingIds\": [" + idsJson + "] }"));
    }

    @Test
    void cancellingMultipleBookedSlotsInOneBatchSucceedsForAll() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Booking first = saveConfirmedBookingAt(clinic, doctor, LocalTime.now().plusHours(1));
        Booking second = saveConfirmedBookingAt(clinic, doctor, LocalTime.now().plusHours(2));
        String token = clinicAdminToken(clinic);

        cancelBatch(
                        clinic.getId().toString(),
                        first.getSlot().getSession().getId().toString(),
                        token,
                        first.getId().toString(),
                        second.getId().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled.length()").value(2))
                .andExpect(jsonPath("$.failed.length()").value(0));

        assertThat(slotRepository.findById(first.getSlot().getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.OPEN);
        assertThat(slotRepository.findById(second.getSlot().getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.OPEN);
        assertThat(bookingRepository.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(bookingRepository.findById(second.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }
}
