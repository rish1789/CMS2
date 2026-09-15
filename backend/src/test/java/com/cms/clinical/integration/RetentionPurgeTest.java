package com.cms.clinical.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.booking.Booking;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.record.Patient;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import org.junit.jupiter.api.Test;

/** 038 US1: T004-T010. */
class RetentionPurgeTest extends AbstractRetentionPurgeIntegrationTest {

    @Test
    void purgesAllThreeContentTypesForAnEligibleBooking() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Patient patient = savePatient(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);
        Booking booking = bookSlotWithCreatedAt(doctor, slot, patient, threeYearsAndOneDayAgo());
        attachConsultationNote(clinic, booking, doctor);
        attachPrescription(clinic, booking, doctor);
        attachExternalRecordReference(clinic, booking, doctor);
        anonymize(clinic, patient);

        int purged = retentionPurgeService.purge();

        assertThat(purged).isEqualTo(1);
        assertThat(consultationNoteRepository.findByBooking_Id(booking.getId())).isEmpty();
        assertThat(prescriptionRepository.findByBooking_Id(booking.getId())).isEmpty();
        assertThat(externalRecordReferenceRepository.findByBooking_Id(booking.getId())).isEmpty();
        assertThat(bookingRepository.findById(booking.getId())).isPresent();
        assertThat(patientRepository.findById(patient.getId())).isPresent();
    }

    @Test
    void leavesContentUntouchedWhenPatientNotAnonymized() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Patient patient = savePatient(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);
        Booking booking = bookSlotWithCreatedAt(doctor, slot, patient, threeYearsAndOneDayAgo());
        attachConsultationNote(clinic, booking, doctor);

        int purged = retentionPurgeService.purge();

        assertThat(purged).isZero();
        assertThat(consultationNoteRepository.findByBooking_Id(booking.getId())).isPresent();
    }

    @Test
    void leavesContentUntouchedWhenBookingIsRecent() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Patient patient = savePatient(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);
        Booking booking = bookSlotWithCreatedAt(doctor, slot, patient, oneYearAgo());
        attachConsultationNote(clinic, booking, doctor);
        anonymize(clinic, patient);

        int purged = retentionPurgeService.purge();

        assertThat(purged).isZero();
        assertThat(consultationNoteRepository.findByBooking_Id(booking.getId())).isPresent();
    }

    @Test
    void purgedBookingStillExposesNonClinicalMetadata() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Patient patient = savePatient(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);
        Booking booking = bookSlotWithCreatedAt(doctor, slot, patient, threeYearsAndOneDayAgo());
        attachConsultationNote(clinic, booking, doctor);
        anonymize(clinic, patient);

        retentionPurgeService.purge();

        Booking refreshed = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(refreshed.getSlot().getId()).isEqualTo(slot.getId());
        assertThat(refreshed.getSlot().getSession().getDoctorProfile().getId()).isEqualTo(doctor.getId());
    }

    @Test
    void skipsBookingsWithNoClinicalContentAttached() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Patient patient = savePatient(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);
        bookSlotWithCreatedAt(doctor, slot, patient, threeYearsAndOneDayAgo());
        anonymize(clinic, patient);

        int purged = retentionPurgeService.purge();

        assertThat(purged).isEqualTo(1);
    }

    @Test
    void rerunningThePurgeIsIdempotent() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Patient patient = savePatient(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);
        Booking booking = bookSlotWithCreatedAt(doctor, slot, patient, threeYearsAndOneDayAgo());
        attachConsultationNote(clinic, booking, doctor);
        anonymize(clinic, patient);

        int firstRun = retentionPurgeService.purge();
        int secondRun = retentionPurgeService.purge();

        assertThat(firstRun).isEqualTo(1);
        assertThat(secondRun).isEqualTo(1);
        assertThat(consultationNoteRepository.findByBooking_Id(booking.getId())).isEmpty();
    }

    @Test
    void purgesMultiplePrescriptionsAndExternalRecordReferencesForABooking() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Patient patient = savePatient(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);
        Booking booking = bookSlotWithCreatedAt(doctor, slot, patient, threeYearsAndOneDayAgo());
        attachPrescription(clinic, booking, doctor);
        attachPrescription(clinic, booking, doctor);
        attachExternalRecordReference(clinic, booking, doctor);
        attachExternalRecordReference(clinic, booking, doctor);
        anonymize(clinic, patient);

        retentionPurgeService.purge();

        assertThat(prescriptionRepository.findByBooking_Id(booking.getId())).isEmpty();
        assertThat(externalRecordReferenceRepository.findByBooking_Id(booking.getId())).isEmpty();
    }
}
