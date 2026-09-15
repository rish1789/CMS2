package com.cms.booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.Booking;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 028 US2 (P2), FR-007/SC-004: a patient cannot cancel a Booking that isn't theirs. */
class PatientBookingCancellationAccessTest extends AbstractBookingCancellationIntegrationTest {

    @Test
    void aDifferentPatientsAttemptToCancelIsRejected() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var ownerAccount = savePatientAccount();
        var unrelatedAccount = savePatientAccount();
        Booking booking = saveConfirmedBookingAt(clinic, doctor, ownerAccount, LocalTime.now().plusHours(3));

        mockMvc.perform(post("/api/v1/patients/bookings/{bookingId}/cancel", booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientToken(unrelatedAccount))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_FOUND"));
    }
}
