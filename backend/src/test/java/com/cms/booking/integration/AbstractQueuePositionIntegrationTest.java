package com.cms.booking.integration;

import com.cms.booking.Booking;
import com.cms.scheduling.SlotStatus;

/**
 * 027: extends 022's queue-booking fixture directly (this codebase's own established
 * {@code com.cms.booking} test-fixture inheritance pattern - mirrors 026's identical
 * {@code com.cms.scheduling} inheritance chain), reusing {@code saveQueueSession}/
 * {@code saveFixedTimeSession}/{@code staffQueueBookingService}/{@code patientQueueBookingService}/
 * every token helper as-is.
 */
public abstract class AbstractQueuePositionIntegrationTest extends AbstractQueueBookingIntegrationTest {

    /** Marks a Booking's own Slot directly - no Queue-mode "complete"/no-show action exists yet in this backlog to trigger it through (Assumptions). */
    protected void setSlotStatus(Booking booking, SlotStatus status) {
        var slot = booking.getSlot();
        slot.setStatus(status);
        slotRepository.save(slot);
    }
}
