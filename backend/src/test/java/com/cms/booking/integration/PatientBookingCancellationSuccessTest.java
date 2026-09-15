package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.Booking;
import com.cms.booking.BookingCancellationReason;
import com.cms.scheduling.SlotStatus;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * 028 US2 (P2), FR-002/SC-001: patient cancels their own Booking more than 2 hours before its
 * scheduled time. patient-cancellation-reason: also proves the collected reason (and optional
 * detail) is actually persisted, not just accepted and discarded.
 */
class PatientBookingCancellationSuccessTest extends AbstractBookingCancellationIntegrationTest {

    @Test
    void patientCancelsOwnBookingMoreThanTwoHoursAheadAndTheReasonIsPersisted() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var patientAccount = savePatientAccount();
        Booking booking = saveConfirmedBookingAt(clinic, doctor, patientAccount, LocalTime.now().plusHours(3));
        String token = patientToken(patientAccount);

        mockMvc.perform(post("/api/v1/patients/bookings/{bookingId}/cancel", booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "reason": "SCHEDULE_CONFLICT", "reasonDetail": "Clashes with a work meeting" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(slotRepository.findById(booking.getSlot().getId()).orElseThrow().getStatus())
                .isEqualTo(SlotStatus.OPEN);

        Booking cancelled = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(cancelled.getCancellationReason()).isEqualTo(BookingCancellationReason.SCHEDULE_CONFLICT);
        assertThat(cancelled.getCancellationReasonDetail()).isEqualTo("Clashes with a work meeting");
    }
}
