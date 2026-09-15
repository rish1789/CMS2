package com.cms.clinical.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.Booking;
import com.cms.booking.BookingStatus;
import com.cms.clinical.ExternalRecordReference;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 036 US1: T007 (create + list), T008 (multiple independent references), T009 (no status precondition), T010 (unknown booking). */
class ExternalRecordReferenceCreateTest extends AbstractExternalRecordReferenceIntegrationTest {

    @Test
    void treatingDoctorCreatesAndListsAReference() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Booking booking = bookSlot(clinic, doctor, slotRepository.findBySession_Id(session.getId()).get(0));

        mockMvc.perform(post(
                                "/api/v1/clinics/{clinicId}/bookings/{bookingId}/external-record-references",
                                clinic.getId(),
                                booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorToken(doctor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"recordType\":\"Lab result\",\"sourceProvider\":\"City Diagnostics\",\"recordDate\":\"2026-08-20\",\"summary\":\"CBC normal.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recordType").value("Lab result"))
                .andExpect(jsonPath("$.summary").value("CBC normal."));

        mockMvc.perform(get(
                                "/api/v1/clinics/{clinicId}/bookings/{bookingId}/external-record-references",
                                clinic.getId(),
                                booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorToken(doctor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void multipleIndependentReferencesPerBookingAreAllowed() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Booking booking = bookSlot(clinic, doctor, slotRepository.findBySession_Id(session.getId()).get(0));

        externalRecordReferenceService.create(
                clinic.getId(),
                booking.getId(),
                doctor.getAccount().getId(),
                "Lab result",
                "City Diagnostics",
                LocalDate.of(2026, 8, 20),
                "CBC normal.");
        externalRecordReferenceService.create(
                clinic.getId(),
                booking.getId(),
                doctor.getAccount().getId(),
                "Imaging report",
                "Metro Radiology",
                LocalDate.of(2026, 8, 21),
                "Chest X-ray clear.");

        List<ExternalRecordReference> references =
                externalRecordReferenceService.list(clinic.getId(), booking.getId(), doctor.getAccount().getId());
        assertThat(references).hasSize(2);
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

        assertThat(externalRecordReferenceService.create(
                        clinic.getId(),
                        cancelledBooking.getId(),
                        doctor.getAccount().getId(),
                        "Lab result",
                        "City Diagnostics",
                        LocalDate.of(2026, 8, 20),
                        "CBC normal."))
                .isNotNull();
        assertThat(externalRecordReferenceService.create(
                        clinic.getId(),
                        completedBooking.getId(),
                        doctor.getAccount().getId(),
                        "Lab result",
                        "City Diagnostics",
                        LocalDate.of(2026, 8, 20),
                        "CBC normal."))
                .isNotNull();
        assertThat(externalRecordReferenceService.create(
                        clinic.getId(),
                        noShowBooking.getId(),
                        doctor.getAccount().getId(),
                        "Lab result",
                        "City Diagnostics",
                        LocalDate.of(2026, 8, 20),
                        "CBC normal."))
                .isNotNull();
    }

    @Test
    void createAndListRejectAnUnknownBooking() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);

        mockMvc.perform(post(
                                "/api/v1/clinics/{clinicId}/bookings/{bookingId}/external-record-references",
                                clinic.getId(),
                                UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorToken(doctor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"recordType\":\"Lab result\",\"sourceProvider\":\"X\",\"recordDate\":\"2026-08-20\",\"summary\":\"X\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_FOUND"));

        mockMvc.perform(get(
                                "/api/v1/clinics/{clinicId}/bookings/{bookingId}/external-record-references",
                                clinic.getId(),
                                UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorToken(doctor)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_FOUND"));
    }
}
