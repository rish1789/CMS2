package com.cms.waitlist.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.booking.domain.Booking;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.waitlist.domain.WaitlistEntry;
import com.cms.waitlist.domain.WaitlistEntryStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 031 US1: FR-009/SC-003 (T022) - 025's individual cancellation is the sole waitlist-bump
 * trigger. Structurally guaranteed by {@code WaitlistBumpListener} only ever listening for
 * {@code BookingCancelledEvent}, which only {@code BookingCancellationService.cancel}
 * publishes - exercised here against all three other release paths this booking module has:
 * 021's automatic no-show sweep, 026's whole-day cancellation, and 027's partial-cutoff
 * cancellation. None of the three ever calls {@code BookingCancellationService}, so none of
 * them ever publishes the event this listener reacts to.
 */
class WaitlistMatchingExclusivityTest extends AbstractWaitlistIntegrationTest {

    @Test
    void noShowReleaseNeverBumpsTheWaitlist() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        Session session = savePastFixedTimeSessionWithSlots(clinic, doctor);
        List<Slot> slots = slotRepository.findBySession_Id(session.getId());
        bookSlot(clinic, doctor, slots.get(0), savePatientAccount());

        WaitlistEntry entry = saveWaitlistEntry(clinic, savePatientAccount(), doctor, null, Instant.now());

        int markedCount = noShowDetectionService.detectAndMarkNoShows();

        assertThat(markedCount).isGreaterThan(0);
        assertThat(waitlistEntryRepository.findById(entry.getId()).orElseThrow().getStatus())
                .isEqualTo(WaitlistEntryStatus.WAITING);
    }

    @Test
    void wholeDaySessionCancellationNeverBumpsTheWaitlist() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<Slot> slots = slotRepository.findBySession_Id(session.getId());
        bookSlot(clinic, doctor, slots.get(0), savePatientAccount());

        WaitlistEntry entry = saveWaitlistEntry(clinic, savePatientAccount(), doctor, null, Instant.now());

        sessionCancellationService.cancelSession(session, doctor.getAccount().getId());

        assertThat(waitlistEntryRepository.findById(entry.getId()).orElseThrow().getStatus())
                .isEqualTo(WaitlistEntryStatus.WAITING);
    }

    @Test
    void partialCutoffSessionCancellationNeverBumpsTheWaitlist() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<Slot> slots = slotRepository.findBySession_Id(session.getId());
        Booking booking = bookSlot(clinic, doctor, slots.get(0), savePatientAccount());

        WaitlistEntry entry = saveWaitlistEntry(clinic, savePatientAccount(), doctor, null, Instant.now());

        sessionPartialCancellationService.cancelFromCutoff(
                session, booking.getSlot().getStartTime(), null, doctor.getAccount().getId());

        assertThat(waitlistEntryRepository.findById(entry.getId()).orElseThrow().getStatus())
                .isEqualTo(WaitlistEntryStatus.WAITING);
    }
}
