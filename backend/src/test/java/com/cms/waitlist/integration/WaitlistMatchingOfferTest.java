package com.cms.waitlist.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.booking.Booking;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.notification.NotificationEvent;
import com.cms.patient.account.PatientAccount;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import com.cms.waitlist.WaitlistEntry;
import com.cms.waitlist.WaitlistEntryStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 031 US1: FR-008/SC-005 (T021) and FR-010 (T023). */
class WaitlistMatchingOfferTest extends AbstractWaitlistIntegrationTest {

    @Test
    void successfulMatchTransitionsEntryAndProducesExactlyOneNotification() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<Slot> slots = slotRepository.findBySession_Id(session.getId());
        Slot slot = slots.get(0);
        Booking booking = bookSlot(clinic, doctor, slot, savePatientAccount());

        PatientAccount waitingPatient = savePatientAccount();
        WaitlistEntry entry = saveWaitlistEntry(clinic, waitingPatient, doctor, null, Instant.now().minusSeconds(60));

        Instant before = Instant.now();
        bookingCancellationService.cancel(booking);
        Instant after = Instant.now();

        WaitlistEntry refreshed = waitlistEntryRepository.findById(entry.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(WaitlistEntryStatus.OFFERED);
        assertThat(refreshed.getOfferedAt()).isBetween(before, after);
        assertThat(refreshed.getOfferExpiresAt()).isBetween(before.plusSeconds(29 * 60), after.plusSeconds(31 * 60));

        List<NotificationEvent> events = notificationEventRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getPatientAccount().getId()).isEqualTo(waitingPatient.getId());
    }

    @Test
    void alreadyOfferedEntryIsNeverSelectedAgain() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<Slot> slots = slotRepository.findBySession_Id(session.getId());

        WaitlistEntry alreadyOffered = saveWaitlistEntry(
                clinic, savePatientAccount(), doctor, null, Instant.now().minus(3, ChronoUnit.DAYS));
        waitlistEntryRepository.saveAndFlush(offer(alreadyOffered, slots.get(1)));
        WaitlistEntry stillWaiting = saveWaitlistEntry(
                clinic, savePatientAccount(), doctor, null, Instant.now().minus(1, ChronoUnit.HOURS));

        Booking booking = bookSlot(clinic, doctor, slots.get(0), savePatientAccount());
        bookingCancellationService.cancel(booking);

        assertThat(waitlistEntryRepository.findById(stillWaiting.getId()).orElseThrow().getStatus())
                .isEqualTo(WaitlistEntryStatus.OFFERED);
    }

    private WaitlistEntry offer(WaitlistEntry entry, Slot slot) {
        entry.offer(Instant.now(), slot);
        return entry;
    }
}
