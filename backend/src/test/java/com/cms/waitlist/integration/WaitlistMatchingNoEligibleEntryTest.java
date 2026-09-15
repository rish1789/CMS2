package com.cms.waitlist.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.booking.Booking;
import com.cms.booking.BookingStatus;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 031 US1: FR-007/SC-004 (T020) - no eligible entry in either tier, cancellation still succeeds. */
class WaitlistMatchingNoEligibleEntryTest extends AbstractWaitlistIntegrationTest {

    @Test
    void cancellationSucceedsAndNoNotificationSentWhenNoWaitlistEntriesExist() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<Slot> slots = slotRepository.findBySession_Id(session.getId());
        Slot slot = slots.get(0);
        Booking booking = bookSlot(clinic, doctor, slot, savePatientAccount());

        Booking cancelled = bookingCancellationService.cancel(booking);

        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(waitlistEntryRepository.findAll()).isEmpty();
        assertThat(notificationEventRepository.findAll()).isEmpty();
    }
}
