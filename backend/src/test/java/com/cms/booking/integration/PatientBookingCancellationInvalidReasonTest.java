package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.Booking;
import com.cms.scheduling.SlotStatus;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** patient-cancellation-reason: a {@code reason} that isn't one of the offered options is rejected outright, and cancels nothing. */
class PatientBookingCancellationInvalidReasonTest extends AbstractBookingCancellationIntegrationTest {

    @Test
    void unrecognizedReasonCodeIsRejected() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var patientAccount = savePatientAccount();
        Booking booking = saveConfirmedBookingAt(clinic, doctor, patientAccount, LocalTime.now().plusHours(3));

        mockMvc.perform(post("/api/v1/patients/bookings/{bookingId}/cancel", booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientToken(patientAccount))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "reason": "CHANGED_MY_MIND_ENTIRELY" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_CANCELLATION_REASON"));

        assertThat(slotRepository.findById(booking.getSlot().getId()).orElseThrow().getStatus())
                .isEqualTo(SlotStatus.BOOKED);
    }
}
