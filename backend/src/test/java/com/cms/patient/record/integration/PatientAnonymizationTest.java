package com.cms.patient.record.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.domain.Booking;
import com.cms.clinical.domain.ConsultationNote;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.account.domain.PatientAccount;
import com.cms.patient.record.domain.Patient;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import java.time.Instant;
import java.util.List;
import com.cms.scheduling.domain.Schedule;
import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.domain.SlotStatus;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
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

    /**
     * real-bug-fix 2026-09-24 (reproduced live): a pending queue booking must block anonymization
     * exactly like a fixed-time one. A real queue token's Slot stays OPEN until the patient is seen
     * (only fixed-time booking flips a Slot to BOOKED), and the precondition query used to require
     * BOOKED - so a patient waiting in tomorrow's queue could be anonymized mid-booking.
     */
    @Test
    void blocksWhileAPendingQueueBookingExists() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Patient patient = savePatient(clinic, null);
        Schedule queueSchedule = scheduleRepository.save(new Schedule(
                doctor, clinic, EnumSet.allOf(DayOfWeek.class),
                LocalTime.of(14, 0), LocalTime.of(16, 0), ScheduleMode.QUEUE, null));
        sessionGenerationService.generate(LocalDate.now());
        Session queueSession = sessionRepository.findBySchedule_Id(queueSchedule.getId()).get(0);
        Slot token = slotRepository.save(new Slot(queueSession, 1));
        bookSlotForPatient(doctor, token, patient);
        token.setStatus(SlotStatus.OPEN); // exactly as the real queue-booking flow leaves it
        slotRepository.save(token);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/patients/{patientId}/anonymize", clinic.getId(), patient.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PATIENT_HAS_ACTIVE_FUTURE_BOOKING"));
        assertThat(patientRepository.findById(patient.getId()).orElseThrow().isAnonymized()).isFalse();
    }

    /**
     * real-bug-fix 2026-09-24: nothing ever resolves a queue token whose patient was never marked
     * seen (the no-show sweep is fixed-time only), so a stale past one must not block anonymization
     * forever - OPEN counts as pending only for today or later.
     */
    @Test
    void aStalePastQueueBookingDoesNotBlockAnonymization() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Patient patient = savePatient(clinic, null);
        Schedule queueSchedule = scheduleRepository.save(new Schedule(
                doctor, clinic, EnumSet.allOf(DayOfWeek.class),
                LocalTime.of(14, 0), LocalTime.of(16, 0), ScheduleMode.QUEUE, null));
        sessionGenerationService.generate(LocalDate.now().minusDays(3));
        Session pastSession = sessionRepository.findBySchedule_Id(queueSchedule.getId()).stream()
                .filter(s -> s.getSessionDate().equals(LocalDate.now().minusDays(3)))
                .findFirst()
                .orElseThrow();
        Slot token = slotRepository.save(new Slot(pastSession, 1));
        bookSlotForPatient(doctor, token, patient);
        token.setStatus(SlotStatus.OPEN);
        slotRepository.save(token);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/patients/{patientId}/anonymize", clinic.getId(), patient.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anonymized").value(true));
    }

    /** 063-front-desk-walk-in (Constitution IV, tasks.md T013): the new optional email is personal data too and is cleared with name and phone. */
    @Test
    void anonymizationAlsoClearsTheEmail() throws Exception {
        Clinic clinic = saveClinic();
        Patient patient = patientRepository.save(new Patient(clinic, null, "Asha Rao", "9999900077", "asha.rao@example.com"));

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/patients/{patientId}/anonymize", clinic.getId(), patient.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk());

        assertThat(patientRepository.findById(patient.getId()).orElseThrow().getEmail()).isNull();
    }

    /**
     * 063-front-desk-walk-in convergence (T039): a Fixed-Time walk-in who was never seen stays an
     * untimed BOOKED slot forever - it must block anonymization only while its session is today or
     * later, never permanently (spec edge case "Walk-ins still waiting at the end of the day").
     */
    @Test
    void aWaitingWalkInBlocksTodayButAStalePastOneDoesNot() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Patient stale = savePatient(clinic, null);
        Patient waitingToday = savePatient(clinic, null);
        Schedule fixed = scheduleRepository.save(new Schedule(
                doctor, clinic, EnumSet.allOf(DayOfWeek.class), LocalTime.of(9, 0), LocalTime.of(13, 0), ScheduleMode.FIXED_TIME, 15));
        sessionGenerationService.generate(LocalDate.now().minusDays(1));
        List<Session> sessions = sessionRepository.findBySchedule_Id(fixed.getId());
        Session yesterday = sessions.stream().filter(s -> s.getSessionDate().equals(LocalDate.now().minusDays(1))).findFirst().orElseThrow();
        Session today = sessions.stream().filter(s -> s.getSessionDate().equals(LocalDate.now())).findFirst().orElseThrow();
        bookSlotForPatient(doctor, slotRepository.save(new Slot(yesterday, 1)), stale);
        bookSlotForPatient(doctor, slotRepository.save(new Slot(today, 1)), waitingToday);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/patients/{patientId}/anonymize", clinic.getId(), stale.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/clinics/{clinicId}/patients/{patientId}/anonymize", clinic.getId(), waitingToday.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PATIENT_HAS_ACTIVE_FUTURE_BOOKING"));
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
