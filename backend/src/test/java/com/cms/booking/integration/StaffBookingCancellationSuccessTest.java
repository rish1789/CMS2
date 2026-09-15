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
import org.springframework.test.web.servlet.ResultActions;

/** 028 US1 (P1), FR-001/FR-004/FR-004a/SC-001/SC-004: staff cancellation succeeds at any time, Slot released, rebookable. */
class StaffBookingCancellationSuccessTest extends AbstractBookingCancellationIntegrationTest {

    private ResultActions cancel(String clinicId, String bookingId, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/bookings/{bookingId}/cancel", clinicId, bookingId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void staffCanCancelRegardlessOfHowCloseTheScheduledTimeIs() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        // Scheduled time already in the past - staff are never subject to any cutoff.
        Booking booking = saveConfirmedBookingAt(clinic, doctor, LocalTime.now().minusMinutes(5));
        String token = clinicAdminToken(clinic);

        cancel(clinic.getId().toString(), booking.getId().toString(), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        var slot = slotRepository.findById(booking.getSlot().getId()).orElseThrow();
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.OPEN);
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(com.cms.booking.BookingStatus.CANCELLED);
    }

    @Test
    void releasedSlotIsBookableAgainByANewBookingWhileTheOriginalRemains() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Booking original = saveConfirmedBookingAt(clinic, doctor, LocalTime.now().plusHours(3));
        String token = clinicAdminToken(clinic);

        cancel(clinic.getId().toString(), original.getId().toString(), token).andExpect(status().isOk());

        var newPatient = patientRepository.save(
                new com.cms.patient.record.Patient(clinic, null, "Rebooking Patient " + java.util.UUID.randomUUID(), null));
        Booking rebooked = bookingRepository.saveAndFlush(new Booking(
                slotRepository.findById(original.getSlot().getId()).orElseThrow(),
                newPatient,
                original.getAppointmentType(),
                original.getLockedFee(),
                doctor.getAccount().getId()));

        assertThat(rebooked.getId()).isNotEqualTo(original.getId());
        assertThat(bookingRepository.findById(original.getId())).isPresent();
        assertThat(bookingRepository.findById(original.getId()).orElseThrow().getStatus())
                .isEqualTo(com.cms.booking.BookingStatus.CANCELLED);
    }
}
