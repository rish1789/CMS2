package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.Booking;
import com.cms.booking.BookingStatus;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** 033 US2: T010 (revoke endpoint cancels across clinics), T011 (idempotency, past/completed untouched), T012 (006's automatic reset never cascades). */
class DoctorRevokeCascadeTest extends AbstractDeVerificationCascadeIntegrationTest {

    @Test
    void revokeCancelsBookingsAcrossClinics() throws Exception {
        DoctorProfile doctor = saveDoctorProfile();
        Clinic clinicA = saveClinic();
        Clinic clinicB = saveClinic();
        linkDoctorToClinic(doctor, clinicA, true);
        linkDoctorToClinic(doctor, clinicB, true);

        Session sessionA = saveFixedTimeSessionWithSlots(clinicA, doctor);
        Booking bookingA = bookSlot(clinicA, doctor, slotRepository.findBySession_Id(sessionA.getId()).get(0));
        Session sessionB = saveFixedTimeSessionWithSlots(clinicB, doctor);
        Booking bookingB = bookSlot(clinicB, doctor, slotRepository.findBySession_Id(sessionB.getId()).get(0));

        mockMvc.perform(post("/api/v1/admin/doctors/{id}/revoke", doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licenseVerified").value(false));

        assertThat(bookingRepository.findById(bookingA.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
        assertThat(bookingRepository.findById(bookingB.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void revokeRejectsAnUnknownDoctor() throws Exception {
        mockMvc.perform(post("/api/v1/admin/doctors/{id}/revoke", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("DOCTOR_PROFILE_NOT_FOUND"));
    }

    @Test
    void revokeRejectsMissingCredentials() throws Exception {
        DoctorProfile doctor = saveDoctorProfile();

        mockMvc.perform(post("/api/v1/admin/doctors/{id}/revoke", doctor.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokeIsIdempotentAndNeverTouchesPastOrCompletedBookings() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<Slot> slots = slotRepository.findBySession_Id(session.getId());
        Booking completedBooking = bookSlot(clinic, doctor, slots.get(0));
        slots.get(0).setStatus(SlotStatus.COMPLETED);
        slotRepository.save(slots.get(0));

        doctorVerificationService.revoke(doctor.getId());
        assertThat(notificationEventRepository.findAll()).isEmpty();
        assertThat(bookingRepository.findById(completedBooking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.ACTIVE);

        // Idempotent: revoking an already-unverified doctor is a no-op.
        doctorVerificationService.revoke(doctor.getId());
        assertThat(notificationEventRepository.findAll()).isEmpty();
    }

    @Test
    void automaticEditTriggeredResetNeverCascades() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Booking activeBooking = bookSlot(clinic, doctor, slotRepository.findBySession_Id(session.getId()).get(0));

        doctorVerificationService.edit(
                doctor.getId(),
                new com.cms.identity.admin.dto.EditDoctorProfileRequest(
                        doctor.getSpecialization(), "DIFFERENT-LICENSE-NUMBER", doctor.getExperienceYears(), true));

        assertThat(doctorProfileRepository.findById(doctor.getId()).orElseThrow().isLicenseVerified())
                .isFalse();
        assertThat(bookingRepository.findById(activeBooking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.ACTIVE);
    }
}
