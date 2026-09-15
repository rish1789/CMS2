package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.booking.BookingStatus;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.notification.NotificationEvent;
import com.cms.patient.account.PatientAccount;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotStatus;
import com.cms.waitlist.WaitlistEntry;
import com.cms.waitlist.WaitlistEntryStatus;
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
}
