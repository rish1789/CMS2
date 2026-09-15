package com.cms.booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.Booking;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * staff-console-audit-2026-09-10 P1: GET /api/v1/clinics/{clinicId}/bookings/{bookingId} - the
 * entity-context lookup backing the booking-scoped staff tool pages' page headers.
 */
class BookingDetailControllerTest extends AbstractSessionCancellationIntegrationTest {

    private Booking bookAnySlot(com.cms.identity.clinic.Clinic clinic, com.cms.identity.doctor.DoctorProfile doctor) {
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<Slot> slots = slotRepository.findBySession_Id(session.getId());
        Slot slot = slots.stream().filter(s -> s.getStartTime().equals(LocalTime.of(9, 0))).findFirst().orElseThrow();
        return bookSlot(clinic, doctor, slot);
    }

    @Test
    void clinicAdminSeesFullBookingDetail() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Booking booking = bookAnySlot(clinic, doctor);
        String token = clinicAdminToken(clinic);

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/bookings/{bookingId}", clinic.getId(), booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(booking.getId().toString()))
                .andExpect(jsonPath("$.sessionId").value(booking.getSlot().getSession().getId().toString()))
                .andExpect(jsonPath("$.patientId").value(booking.getPatient().getId().toString()))
                .andExpect(jsonPath("$.patientName").value(booking.getPatient().getName()))
                .andExpect(jsonPath("$.doctorProfileId").value(doctor.getId().toString()))
                .andExpect(jsonPath("$.doctorName").value(doctor.getAccount().getName()))
                .andExpect(jsonPath("$.sessionDate").value("2026-09-03"))
                .andExpect(jsonPath("$.mode").value("FIXED_TIME"))
                .andExpect(jsonPath("$.appointmentTypeName").value("Consultation"));
    }

    @Test
    void theBookingsOwnDoctorCanSeeItsDetail() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Booking booking = bookAnySlot(clinic, doctor);
        String token = doctorToken(doctor);

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/bookings/{bookingId}", clinic.getId(), booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientName").value(booking.getPatient().getName()));
    }

    @Test
    void aDifferentDoctorAtTheSameClinicGetsNotFoundNotForbidden() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var otherDoctor = saveDoctorStaffedAt(clinic);
        Booking booking = bookAnySlot(clinic, doctor);
        String token = doctorToken(otherDoctor);

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/bookings/{bookingId}", clinic.getId(), booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_FOUND"));
    }

    @Test
    void staffNotAssignedToTheClinicAtAllIsForbidden() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Booking booking = bookAnySlot(clinic, doctor);
        String token = unrelatedStaffToken();

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/bookings/{bookingId}", clinic.getId(), booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void unknownBookingIdIsNotFound() throws Exception {
        var clinic = saveClinic();
        String token = clinicAdminToken(clinic);

        mockMvc.perform(get(
                        "/api/v1/clinics/{clinicId}/bookings/{bookingId}",
                        clinic.getId(),
                        java.util.UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_FOUND"));
    }

    @Test
    void aBookingAtAnotherClinicIsNotFound() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Booking booking = bookAnySlot(clinic, doctor);

        var otherClinic = saveClinic();
        String token = clinicAdminToken(otherClinic);

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/bookings/{bookingId}", otherClinic.getId(), booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_FOUND"));
    }
}
