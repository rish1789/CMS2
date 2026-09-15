package com.cms.clinical.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.Booking;
import com.cms.booking.BookingStatus;
import com.cms.clinical.ConsultationNote;
import com.cms.clinical.ConsultationNoteAlreadyExistsException;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotStatus;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 034 US1: T009 (create + get), T010 (second-creation rejection + concurrency), T012 (unknown booking), T012a (no status precondition, FR-007). */
class ConsultationNoteCreateTest extends AbstractConsultationNoteIntegrationTest {

    @Test
    void treatingDoctorCreatesAndRetrievesANote() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Booking booking = bookSlot(clinic, doctor, slotRepository.findBySession_Id(session.getId()).get(0));

        mockMvc.perform(post(
                                "/api/v1/clinics/{clinicId}/bookings/{bookingId}/consultation-notes",
                                clinic.getId(),
                                booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorToken(doctor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Patient presented with mild fever.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Patient presented with mild fever."));

        mockMvc.perform(get(
                                "/api/v1/clinics/{clinicId}/bookings/{bookingId}/consultation-notes",
                                clinic.getId(),
                                booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorToken(doctor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Patient presented with mild fever."));
    }

    @Test
    void rejectsASecondNoteForTheSameBooking() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Booking booking = bookSlot(clinic, doctor, slotRepository.findBySession_Id(session.getId()).get(0));

        consultationNoteService.create(clinic.getId(), booking.getId(), doctor.getAccount().getId(), "First note.");

        assertThatThrownBy(() -> consultationNoteService.create(
                        clinic.getId(), booking.getId(), doctor.getAccount().getId(), "Second attempt."))
                .isInstanceOf(ConsultationNoteAlreadyExistsException.class);
    }

    @Test
    void concurrentCreationAttemptsOnlyOneSucceeds() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Booking booking = bookSlot(clinic, doctor, slotRepository.findBySession_Id(session.getId()).get(0));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> attempt = () -> {
                try {
                    consultationNoteService.create(
                            clinic.getId(), booking.getId(), doctor.getAccount().getId(), "Racing note.");
                    return true;
                } catch (ConsultationNoteAlreadyExistsException e) {
                    return false;
                }
            };

            List<Future<Boolean>> futures = executor.invokeAll(List.of(attempt, attempt));
            long winCount = futures.stream()
                    .filter(f -> {
                        try {
                            return f.get();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .count();

            assertThat(winCount).isEqualTo(1);
            assertThat(consultationNoteRepository.findByBooking_Id(booking.getId())).isPresent();
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void createAndGetRejectAnUnknownBooking() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);

        mockMvc.perform(post(
                                "/api/v1/clinics/{clinicId}/bookings/{bookingId}/consultation-notes",
                                clinic.getId(),
                                UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorToken(doctor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"...\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_FOUND"));

        mockMvc.perform(get(
                                "/api/v1/clinics/{clinicId}/bookings/{bookingId}/consultation-notes",
                                clinic.getId(),
                                UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorToken(doctor)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_FOUND"));
    }

    @Test
    void creationSucceedsRegardlessOfSlotStatus() {
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

        ConsultationNote cancelledNote = consultationNoteService.create(
                clinic.getId(), cancelledBooking.getId(), doctor.getAccount().getId(), "Cancelled booking note.");
        ConsultationNote completedNote = consultationNoteService.create(
                clinic.getId(), completedBooking.getId(), doctor.getAccount().getId(), "Completed visit note.");
        ConsultationNote noShowNote = consultationNoteService.create(
                clinic.getId(), noShowBooking.getId(), doctor.getAccount().getId(), "No-show note.");

        assertThat(cancelledNote.getId()).isNotNull();
        assertThat(completedNote.getId()).isNotNull();
        assertThat(noShowNote.getId()).isNotNull();
    }
}
