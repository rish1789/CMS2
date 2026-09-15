package com.cms.clinical.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.Booking;
import com.cms.booking.BookingStatus;
import com.cms.clinical.Prescription;
import com.cms.clinical.PrescriptionItemRequiredException;
import com.cms.clinical.dto.PrescriptionItemRequest;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 035 US1: T011 (create + list), T012 (multiple independent prescriptions), T013 (zero-item rejection + no status precondition), T014 (unknown booking). */
class PrescriptionCreateTest extends AbstractPrescriptionIntegrationTest {

    @Test
    void treatingDoctorCreatesAndListsAPrescription() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Booking booking = bookSlot(clinic, doctor, slotRepository.findBySession_Id(session.getId()).get(0));

        mockMvc.perform(post(
                                "/api/v1/clinics/{clinicId}/bookings/{bookingId}/prescriptions",
                                clinic.getId(),
                                booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorToken(doctor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"items\":[{\"medicationName\":\"Amoxicillin\",\"dosage\":\"500mg\",\"frequency\":\"3x daily\",\"duration\":\"7 days\",\"instructions\":\"With food\"},"
                                        + "{\"medicationName\":\"Paracetamol\",\"dosage\":\"650mg\",\"frequency\":\"as needed\",\"duration\":\"5 days\"}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].medicationName").value("Amoxicillin"));

        // Regression: prescriptionRepository.findByBooking_Id returns a Prescription
        // genuinely loaded fresh from the database (unlike the POST above, which mapped a
        // just-constructed instance) - Prescription.items is an @OneToMany, LAZY by JPA
        // default, and this GET's @Transactional(readOnly = true) boundary closes before
        // the controller maps to PrescriptionResponse (open-in-view: false), so a lazy
        // collection here throws LazyInitializationException on every call (fixed:
        // Prescription.items is now EAGER, the same bug class as Schedule.daysOfWeek).
        mockMvc.perform(get(
                                "/api/v1/clinics/{clinicId}/bookings/{bookingId}/prescriptions",
                                clinic.getId(),
                                booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorToken(doctor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].items.length()").value(2))
                .andExpect(jsonPath("$[0].items[0].medicationName").value("Amoxicillin"))
                .andExpect(jsonPath("$[0].items[1].medicationName").value("Paracetamol"));
    }

    @Test
    void multipleIndependentPrescriptionsPerBookingAreAllowed() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Booking booking = bookSlot(clinic, doctor, slotRepository.findBySession_Id(session.getId()).get(0));

        prescriptionService.create(
                clinic.getId(),
                booking.getId(),
                doctor.getAccount().getId(),
                List.of(new PrescriptionItemRequest("Amoxicillin", "500mg", "3x daily", "7 days", null)));
        prescriptionService.create(
                clinic.getId(),
                booking.getId(),
                doctor.getAccount().getId(),
                List.of(new PrescriptionItemRequest("Ibuprofen", "200mg", "2x daily", "3 days", null)));

        List<Prescription> prescriptions =
                prescriptionService.list(clinic.getId(), booking.getId(), doctor.getAccount().getId());
        assertThat(prescriptions).hasSize(2);
    }

    @Test
    void rejectsAnEmptyItemList() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Booking booking = bookSlot(clinic, doctor, slotRepository.findBySession_Id(session.getId()).get(0));

        mockMvc.perform(post(
                                "/api/v1/clinics/{clinicId}/bookings/{bookingId}/prescriptions",
                                clinic.getId(),
                                booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorToken(doctor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PRESCRIPTION_ITEM_REQUIRED"));
    }

    @Test
    void creationSucceedsRegardlessOfSlotOrBookingStatus() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<Slot> slots = slotRepository.findBySession_Id(session.getId());

        Booking cancelledBooking = bookSlot(clinic, doctor, slots.get(0));
        cancelledBooking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(cancelledBooking);
        Booking completedBooking = bookSlot(clinic, doctor, slots.get(1));
        slots.get(1).setStatus(SlotStatus.COMPLETED);
        slotRepository.save(slots.get(1));
        Booking noShowBooking = bookSlot(clinic, doctor, slots.get(2));
        slots.get(2).setStatus(SlotStatus.NO_SHOW);
        slotRepository.save(slots.get(2));

        List<PrescriptionItemRequest> items =
                List.of(new PrescriptionItemRequest("Amoxicillin", "500mg", "3x daily", "7 days", null));

        assertThat(prescriptionService.create(clinic.getId(), cancelledBooking.getId(), doctor.getAccount().getId(), items))
                .isNotNull();
        assertThat(prescriptionService.create(clinic.getId(), completedBooking.getId(), doctor.getAccount().getId(), items))
                .isNotNull();
        assertThat(prescriptionService.create(clinic.getId(), noShowBooking.getId(), doctor.getAccount().getId(), items))
                .isNotNull();
    }

    @Test
    void createAndListRejectAnUnknownBooking() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);

        mockMvc.perform(post(
                                "/api/v1/clinics/{clinicId}/bookings/{bookingId}/prescriptions",
                                clinic.getId(),
                                UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorToken(doctor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"medicationName\":\"X\",\"dosage\":\"1\",\"frequency\":\"1\",\"duration\":\"1\"}]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_FOUND"));

        mockMvc.perform(get(
                                "/api/v1/clinics/{clinicId}/bookings/{bookingId}/prescriptions",
                                clinic.getId(),
                                UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorToken(doctor)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_FOUND"));
    }

    @Test
    void serviceRejectsAnEmptyItemListDirectly() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Booking booking = bookSlot(clinic, doctor, slotRepository.findBySession_Id(session.getId()).get(0));

        assertThatThrownBy(() -> prescriptionService.create(
                        clinic.getId(), booking.getId(), doctor.getAccount().getId(), List.of()))
                .isInstanceOf(PrescriptionItemRequiredException.class);
    }
}
