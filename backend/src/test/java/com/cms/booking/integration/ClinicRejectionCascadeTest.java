package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingCancellationReason;
import com.cms.booking.domain.BookingStatus;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.account.domain.PatientAccount;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.waitlist.domain.WaitlistEntry;
import com.cms.waitlist.domain.WaitlistEntryStatus;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 062-rejected-clinic-gating (FR-009/FR-010/FR-011, tasks.md T009): rejecting a clinic cancels its
 * upcoming bookings with CLINIC_REJECTED, frees their slots, never triggers a waitlist offer, leaves
 * past and already-attended bookings alone, and expires the clinic's waitlist. Every assertion
 * reads back through a fresh repository query after the reject call returns - proving the
 * AFTER_COMMIT cascade's writes actually persisted (analyze finding C1).
 */
class ClinicRejectionCascadeTest extends AbstractDeVerificationCascadeIntegrationTest {

    private Clinic pendingClinic() {
        Clinic clinic = saveClinic();
        clinic.setVerified(false); // only a Pending clinic can be rejected
        return clinicRepository.save(clinic);
    }

    private Session sessionOn(Session anySessionOfSchedule, LocalDate date) {
        return sessionRepository.findBySchedule_Id(anySessionOfSchedule.getSchedule().getId()).stream()
                .filter(s -> s.getSessionDate().equals(date))
                .findFirst()
                .orElseThrow();
    }

    private List<Slot> slotsOf(Session session) {
        return slotRepository.findBySession_Id(session.getId()).stream()
                .sorted(Comparator.comparing(Slot::getStartTime))
                .toList();
    }

    @Test
    void rejectionCancelsUpcomingBookingsOnlyWithoutWaitlistOffersAndExpiresTheWaitlist() {
        Clinic clinic = pendingClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        PatientAccount patient = savePatientAccount();
        PatientAccount waitingPatient = savePatientAccount();

        Session any = saveFixedTimeSessionWithSlots(clinic, doctor);
        Session today = sessionOn(any, LocalDate.now());
        // A session dated yesterday for the same schedule - a past appointment the cascade must not touch.
        sessionGenerationService.generate(LocalDate.now().minusDays(1));
        Session yesterday = sessionOn(today, LocalDate.now().minusDays(1));
        Session tomorrow = sessionOn(today, LocalDate.now().plusDays(1));

        Booking upcoming = bookSlot(clinic, doctor, slotsOf(tomorrow).get(0), patient);
        Booking past = bookSlot(clinic, doctor, slotsOf(yesterday).get(0));
        Slot appearedSlot = slotsOf(today).get(0);
        Booking attended = bookSlot(clinic, doctor, appearedSlot);
        appearedSlot.setStatus(SlotStatus.APPEARED);
        slotRepository.save(appearedSlot);
        WaitlistEntry waiting = saveWaitingEntry(clinic, doctor, waitingPatient);

        clinicVerificationService.reject(clinic.getId(), "DUPLICATE_REGISTRATION", null, SUPER_ADMIN_USERNAME);

        Booking upcomingAfter = bookingRepository.findById(upcoming.getId()).orElseThrow();
        assertThat(upcomingAfter.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(upcomingAfter.getCancellationReason()).isEqualTo(BookingCancellationReason.CLINIC_REJECTED);
        assertThat(slotRepository.findById(upcoming.getSlot().getId()).orElseThrow().getStatus())
                .isEqualTo(SlotStatus.OPEN);

        assertThat(bookingRepository.findById(past.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.ACTIVE);
        assertThat(bookingRepository.findById(attended.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.ACTIVE);

        // FR-009: the freed slot is never offered - the waiting entry goes straight to EXPIRED (FR-011).
        assertThat(waitlistEntryRepository.findById(waiting.getId()).orElseThrow().getStatus())
                .isEqualTo(WaitlistEntryStatus.EXPIRED);
    }

    @Test
    void rejectingAnAlreadyRejectedClinicAgainCancelsNothingNew() {
        Clinic clinic = pendingClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session any = saveFixedTimeSessionWithSlots(clinic, doctor);
        Session tomorrow = sessionOn(any, LocalDate.now().plusDays(1));

        clinicVerificationService.reject(clinic.getId(), "DUPLICATE_REGISTRATION", null, SUPER_ADMIN_USERNAME);
        // A booking that somehow exists after rejection (e.g. written directly) is not swept by a
        // repeated, idempotent reject - no second event is published.
        Booking later = bookSlot(clinic, doctor, slotsOf(tomorrow).get(1));
        clinicVerificationService.reject(clinic.getId(), "DUPLICATE_REGISTRATION", null, SUPER_ADMIN_USERNAME);

        assertThat(bookingRepository.findById(later.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.ACTIVE);
    }

    /**
     * real-bug-fix 2026-09-24: a queue booking exactly as the real flow leaves it - the token Slot
     * stays OPEN (only fixed-time booking flips a Slot to BOOKED). The shared {@code bookSlot}
     * fixture forces BOOKED, which is what hid the cascade's queue-mode gap from this suite.
     */
    private com.cms.booking.domain.Booking bookQueueTokenAsTheRealFlowDoes(
            Clinic clinic, DoctorProfile doctor, Session queueSession, PatientAccount patientAccount) {
        Slot token = addQueueSlot(queueSession, 1);
        var booking = bookSlot(clinic, doctor, token, patientAccount);
        token.setStatus(SlotStatus.OPEN);
        slotRepository.save(token);
        return booking;
    }

    /** real-bug-fix 2026-09-24: the same queue-mode gap as 008 - a real-flow queue token's Slot is OPEN, not BOOKED. */
    @Test
    void rejectionAlsoCancelsARealFlowQueueBooking() {
        Clinic clinic = pendingClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        PatientAccount patient = savePatientAccount();
        var queueBooking = bookQueueTokenAsTheRealFlowDoes(clinic, doctor, saveQueueSession(clinic, doctor), patient);

        clinicVerificationService.reject(clinic.getId(), "DUPLICATE_REGISTRATION", null, SUPER_ADMIN_USERNAME);

        var after = bookingRepository.findById(queueBooking.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(after.getCancellationReason()).isEqualTo(BookingCancellationReason.CLINIC_REJECTED);
    }
}
