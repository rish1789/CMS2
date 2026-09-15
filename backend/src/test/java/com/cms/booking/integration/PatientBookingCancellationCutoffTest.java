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

/** 028 US2 (P2), FR-002/SC-002: the 2-hour cutoff blocks patient self-service, but never staff. */
class PatientBookingCancellationCutoffTest extends AbstractBookingCancellationIntegrationTest {

    @Test
    void patientWithinTwoHoursIsBlockedButStaffCanStillCancelOnTheirBehalf() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var patientAccount = savePatientAccount();
        Booking booking = saveConfirmedBookingAt(clinic, doctor, patientAccount, LocalTime.now().plusMinutes(90));
        String patientTokenValue = patientToken(patientAccount);

        mockMvc.perform(post("/api/v1/patients/bookings/{bookingId}/cancel", booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientTokenValue)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CANCELLATION_CUTOFF_PASSED"));

        assertThat(slotRepository.findById(booking.getSlot().getId()).orElseThrow().getStatus())
                .isEqualTo(SlotStatus.BOOKED);

        String staffToken = clinicAdminToken(clinic);
        mockMvc.perform(post("/api/v1/clinics/{clinicId}/bookings/{bookingId}/cancel", clinic.getId(), booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
