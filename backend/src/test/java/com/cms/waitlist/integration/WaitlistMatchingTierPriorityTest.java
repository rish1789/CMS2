package com.cms.waitlist.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.booking.Booking;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.account.PatientAccount;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import com.cms.waitlist.WaitlistEntry;
import com.cms.waitlist.WaitlistEntryStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 031 US1: FR-004/FR-006/SC-001 (T018) and FR-005/SC-002 (T019). */
class WaitlistMatchingTierPriorityTest extends AbstractWaitlistIntegrationTest {

    @Test
    void doctorMatchOutranksSpecializationOnlyRegardlessOfWaitTime() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<Slot> slots = slotRepository.findBySession_Id(session.getId());
        Slot slot = slots.get(0);
        Booking booking = bookSlot(clinic, doctor, slot, savePatientAccount());

        Instant now = Instant.now();
        PatientAccount doctorMatchPatient = savePatientAccount();
        WaitlistEntry doctorMatchEntry =
                saveWaitlistEntry(clinic, doctorMatchPatient, doctor, null, now.minus(1, ChronoUnit.HOURS));
        PatientAccount specializationOnlyPatient = savePatientAccount();
        WaitlistEntry specializationOnlyEntry = saveWaitlistEntry(
                clinic, specializationOnlyPatient, null, "Cardiology", now.minus(3, ChronoUnit.DAYS));

        bookingCancellationService.cancel(booking);

        WaitlistEntry refreshedDoctorMatch = waitlistEntryRepository.findById(doctorMatchEntry.getId()).orElseThrow();
        WaitlistEntry refreshedSpecializationOnly =
                waitlistEntryRepository.findById(specializationOnlyEntry.getId()).orElseThrow();
        assertThat(refreshedDoctorMatch.getStatus()).isEqualTo(WaitlistEntryStatus.OFFERED);
        assertThat(refreshedSpecializationOnly.getStatus()).isEqualTo(WaitlistEntryStatus.WAITING);
    }

    @Test
    void longestWaitingWithinATierIsOffered() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<Slot> slots = slotRepository.findBySession_Id(session.getId());
        Slot slot = slots.get(0);
        Booking booking = bookSlot(clinic, doctor, slot, savePatientAccount());

        Instant now = Instant.now();
        WaitlistEntry earlier =
                saveWaitlistEntry(clinic, savePatientAccount(), doctor, null, now.minus(3, ChronoUnit.DAYS));
        WaitlistEntry later = saveWaitlistEntry(clinic, savePatientAccount(), doctor, null, now.minus(1, ChronoUnit.HOURS));

        bookingCancellationService.cancel(booking);

        assertThat(waitlistEntryRepository.findById(earlier.getId()).orElseThrow().getStatus())
                .isEqualTo(WaitlistEntryStatus.OFFERED);
        assertThat(waitlistEntryRepository.findById(later.getId()).orElseThrow().getStatus())
                .isEqualTo(WaitlistEntryStatus.WAITING);
    }
}
