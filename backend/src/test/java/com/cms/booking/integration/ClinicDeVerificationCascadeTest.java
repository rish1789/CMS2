package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.booking.domain.BookingStatus;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.notification.domain.NotificationEvent;
import com.cms.patient.account.domain.PatientAccount;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.waitlist.domain.WaitlistEntry;
import com.cms.waitlist.domain.WaitlistEntryStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 033 US1: T005 (cancels every active booking, leaves completed untouched), T006 (real waitlist bump for fixed-time only), T007 (notifications, idempotency, FR-008). */
class ClinicDeVerificationCascadeTest extends AbstractDeVerificationCascadeIntegrationTest {

    @Test
    void unverifyingCancelsEveryActiveBookingAcrossDoctorsAndModesButLeavesCompletedUntouched() {
        Clinic clinic = saveClinic();
        DoctorProfile doctorA = saveDoctorStaffedAt(clinic);
        DoctorProfile doctorB = saveDoctorStaffedAt(clinic);

        Session fixedSessionA = saveFixedTimeSessionWithSlots(clinic, doctorA);
        List<Slot> fixedSlotsA = slotRepository.findBySession_Id(fixedSessionA.getId());
        var activeFixed = bookSlot(clinic, doctorA, fixedSlotsA.get(0));
        var completedBooking = bookSlot(clinic, doctorA, fixedSlotsA.get(1));
        fixedSlotsA.get(1).setStatus(SlotStatus.COMPLETED);
        slotRepository.save(fixedSlotsA.get(1));

        Session queueSession = saveQueueSession(clinic, doctorB);
        Slot queueSlot = addQueueSlot(queueSession, 1);
        var activeQueue = bookSlot(clinic, doctorB, queueSlot);

        clinicVerificationService.unverify(clinic.getId());

        assertThat(bookingRepository.findById(activeFixed.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
        assertThat(bookingRepository.findById(activeQueue.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
        assertThat(bookingRepository.findById(completedBooking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.ACTIVE);
        assertThat(slotRepository.findById(queueSlot.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.OPEN);
    }

    @Test
    void cancelledFixedTimeBookingProducesARealWaitlistOfferButQueueModeDoesNot() {
        Clinic clinic = saveClinic();
        DoctorProfile doctorA = saveDoctorStaffedAt(clinic);
        DoctorProfile doctorB = saveDoctorStaffedAt(clinic);
        PatientAccount waitingPatient = savePatientAccount();
        WaitlistEntry entry = saveWaitingEntry(clinic, doctorA, waitingPatient);

        Session fixedSessionA = saveFixedTimeSessionWithSlots(clinic, doctorA);
        bookSlot(clinic, doctorA, slotRepository.findBySession_Id(fixedSessionA.getId()).get(0));

        Session queueSession = saveQueueSession(clinic, doctorB);
        Slot queueSlot = addQueueSlot(queueSession, 1);
        bookSlot(clinic, doctorB, queueSlot);

        clinicVerificationService.unverify(clinic.getId());

        assertThat(waitlistEntryRepository.findById(entry.getId()).orElseThrow().getStatus())
                .isEqualTo(WaitlistEntryStatus.OFFERED);
    }

    @Test
    void notifiesLinkedPatientsSkipsWalkInsAndIsIdempotentAndDoesNotRestoreOnReVerify() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<Slot> slots = slotRepository.findBySession_Id(session.getId());

        PatientAccount linkedPatientAccount = savePatientAccount();
        var linkedBooking = bookSlot(clinic, doctor, slots.get(0), linkedPatientAccount);
        var walkInBooking = bookSlot(clinic, doctor, slots.get(1));

        clinicVerificationService.unverify(clinic.getId());

        List<NotificationEvent> events = notificationEventRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getPatientAccount().getId()).isEqualTo(linkedPatientAccount.getId());

        // Idempotent: re-unverifying an already-unverified clinic is a no-op.
        clinicVerificationService.unverify(clinic.getId());
        assertThat(notificationEventRepository.findAll()).hasSize(1);

        // FR-008: re-verifying does not restore the cascade-cancelled bookings.
        clinicVerificationService.verify(clinic.getId());
        assertThat(bookingRepository.findById(linkedBooking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
        assertThat(bookingRepository.findById(walkInBooking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
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

    /**
     * real-bug-fix 2026-09-24 (reproduced live): un-verifying left a real-flow queue booking ACTIVE
     * (the query required a BOOKED slot) and dropped every patient notification (the cascade joined
     * the already-committed transaction). Both must now persist.
     */
    @Test
    void unverifyingCancelsARealFlowQueueBookingAndPersistsEveryPatientNotification() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        PatientAccount queuePatient = savePatientAccount();
        PatientAccount fixedPatient = savePatientAccount();
        var queueBooking = bookQueueTokenAsTheRealFlowDoes(clinic, doctor, saveQueueSession(clinic, doctor), queuePatient);
        Session fixed = saveFixedTimeSessionWithSlots(clinic, doctor);
        bookSlot(clinic, doctor, slotRepository.findBySession_Id(fixed.getId()).get(0), fixedPatient);

        clinicVerificationService.unverify(clinic.getId());

        assertThat(bookingRepository.findById(queueBooking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
        assertThat(notificationEventRepository.findAll())
                .extracting(event -> event.getPatientAccount().getId())
                .containsExactlyInAnyOrder(queuePatient.getId(), fixedPatient.getId());
    }
}
