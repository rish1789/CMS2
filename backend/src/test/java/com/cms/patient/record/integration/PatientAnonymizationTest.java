package com.cms.patient.record.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.Booking;
import com.cms.clinical.ConsultationNote;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.account.PatientAccount;
import com.cms.patient.record.Patient;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** 037 US1: T009 (successful anonymization), T010 (blocked-then-retried), T011 (idempotent retry), T012 (historical records/account untouched). */
class PatientAnonymizationTest extends AbstractPatientAnonymizationIntegrationTest {

    @Test
    void anonymizesAPatientWithNoActiveFutureBookings() throws Exception {
        Clinic clinic = saveClinic();
        Patient patient = savePatient(clinic, null);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/patients/{patientId}/anonymize", clinic.getId(), patient.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anonymized").value(true));

        Patient refreshed = patientRepository.findById(patient.getId()).orElseThrow();
        assertThat(refreshed.getName()).isEqualTo("Anonymized Patient");
        assertThat(refreshed.getPhone()).isNull();
        assertThat(refreshed.isAnonymized()).isTrue();
    }

    @Test
    void blocksWhileAnActiveFutureBookingExistsThenSucceedsAfterCancellation() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Patient patient = savePatient(clinic, null);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);
        Booking booking = bookSlotForPatient(doctor, slot, patient);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/patients/{patientId}/anonymize", clinic.getId(), patient.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PATIENT_HAS_ACTIVE_FUTURE_BOOKING"));

        Patient stillUnmodified = patientRepository.findById(patient.getId()).orElseThrow();
        assertThat(stillUnmodified.isAnonymized()).isFalse();
        assertThat(stillUnmodified.getPhone()).isNotNull();

        int updated = bookingRepository.cancelIfActive(booking.getId());
        assertThat(updated).isEqualTo(1);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/patients/{patientId}/anonymize", clinic.getId(), patient.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anonymized").value(true));
    }

    @Test
    void repeatedAnonymizationIsIdempotentAndPreservesTheOriginalTimestamp() {
        Clinic clinic = saveClinic();
        Patient patient = savePatient(clinic, null);

        Patient first = patientAnonymizationService.anonymize(clinic.getId(), patient.getId());
        Instant firstTimestamp = first.getAnonymizedAt();

        Patient second = patientAnonymizationService.anonymize(clinic.getId(), patient.getId());

        assertThat(second.getAnonymizedAt()).isEqualTo(firstTimestamp);
    }

    @Test
    void leavesHistoricalRecordsAndTheLinkedPatientAccountUntouched() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        PatientAccount patientAccount = savePatientAccount();
        Patient patient = savePatient(clinic, patientAccount);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);
        Booking booking = bookSlotForPatient(doctor, slot, patient);
        ConsultationNote note =
                consultationNoteService.create(clinic.getId(), booking.getId(), doctor.getAccount().getId(), "Visit note.");

        bookingRepository.cancelIfActive(booking.getId());
        patientAnonymizationService.anonymize(clinic.getId(), patient.getId());

        List<Booking> bookings = bookingRepository.findAll();
        assertThat(bookings).extracting(Booking::getId).contains(booking.getId());
        assertThat(consultationNoteRepository.findById(note.getId())).isPresent();
        assertThat(patientAccountRepository.findById(patientAccount.getId())).isPresent();
        PatientAccount refreshedAccount = patientAccountRepository.findById(patientAccount.getId()).orElseThrow();
        assertThat(refreshedAccount.getEmail()).isEqualTo(patientAccount.getEmail());
        Patient refreshedPatient = patientRepository.findById(patient.getId()).orElseThrow();
        assertThat(refreshedPatient.getPatientAccount().getId()).isEqualTo(patientAccount.getId());
    }
}
