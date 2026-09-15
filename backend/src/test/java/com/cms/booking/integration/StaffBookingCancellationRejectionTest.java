package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.AppointmentType;
import com.cms.booking.Booking;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.record.Patient;
import com.cms.scheduling.Schedule;
import com.cms.scheduling.ScheduleMode;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotStatus;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 028 US1 (P1), FR-003/FR-009: rejections for already-resolved states and Queue-mode. */
class StaffBookingCancellationRejectionTest extends AbstractBookingCancellationIntegrationTest {

    private ResultActions cancel(String clinicId, String bookingId, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/bookings/{bookingId}/cancel", clinicId, bookingId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void cancellingAnAlreadyCancelledBookingIsRejectedAgain() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Booking booking = saveConfirmedBookingAt(clinic, doctor, LocalTime.now().plusHours(3));
        String token = clinicAdminToken(clinic);

        cancel(clinic.getId().toString(), booking.getId().toString(), token).andExpect(status().isOk());

        cancel(clinic.getId().toString(), booking.getId().toString(), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_CANCELLABLE"));
    }

    @Test
    void cancellingANoShowBookingIsRejected() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Booking booking = saveConfirmedBookingAt(clinic, doctor, LocalTime.now().minusHours(1));
        booking.getSlot().setStatus(SlotStatus.NO_SHOW);
        slotRepository.save(booking.getSlot());
        String token = clinicAdminToken(clinic);

        cancel(clinic.getId().toString(), booking.getId().toString(), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_CANCELLABLE"));

        assertThat(slotRepository.findById(booking.getSlot().getId()).orElseThrow().getStatus())
                .isEqualTo(SlotStatus.NO_SHOW);
    }

    @Test
    void cancellingACompletedBookingIsRejected() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Booking booking = saveConfirmedBookingAt(clinic, doctor, LocalTime.now().minusHours(1));
        booking.getSlot().setStatus(SlotStatus.COMPLETED);
        slotRepository.save(booking.getSlot());
        String token = clinicAdminToken(clinic);

        cancel(clinic.getId().toString(), booking.getId().toString(), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_CANCELLABLE"));
    }

    @Test
    void cancellingAQueueModeBookingIsRejected() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Booking booking = saveQueueModeBooking(clinic, doctor);
        String token = clinicAdminToken(clinic);

        cancel(clinic.getId().toString(), booking.getId().toString(), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NOT_A_FIXED_TIME_SESSION"));
    }

    private Booking saveQueueModeBooking(Clinic clinic, DoctorProfile doctor) {
        Schedule schedule = scheduleRepository.save(new Schedule(
                doctor, clinic, EnumSet.allOf(DayOfWeek.class), LocalTime.of(9, 0), LocalTime.of(13, 0), ScheduleMode.QUEUE, null));
        Session session = sessionRepository.save(
                new Session(schedule, clinic, doctor, LocalDate.now(), ScheduleMode.QUEUE, LocalTime.of(9, 0), LocalTime.of(13, 0), null));
        Slot slot = slotRepository.save(new Slot(session, 1));
        slot.setStatus(SlotStatus.BOOKED);
        slot = slotRepository.save(slot);
        AppointmentType appointmentType = appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", new BigDecimal("300.00")));
        Patient patient = patientRepository.save(new Patient(clinic, null, "Queue Patient " + UUID.randomUUID(), null));
        return bookingRepository.saveAndFlush(
                new Booking(slot, patient, appointmentType, new BigDecimal("300.00"), doctor.getAccount().getId()));
    }
}
