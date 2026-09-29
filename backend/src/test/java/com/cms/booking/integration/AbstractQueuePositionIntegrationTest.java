package com.cms.booking.integration;

import com.cms.booking.domain.Booking;
import com.cms.scheduling.domain.SlotStatus;

/**
 * 027: extends 022's queue-booking fixture directly (this codebase's own established
 * {@code com.cms.booking} test-fixture inheritance pattern - mirrors 026's identical
 * {@code com.cms.scheduling} inheritance chain), reusing {@code saveQueueSession}/
 * {@code saveFixedTimeSession}/{@code staffQueueBookingService}/{@code patientQueueBookingService}/
 * every token helper as-is.
 */
public abstract class AbstractQueuePositionIntegrationTest extends AbstractQueueBookingIntegrationTest {

    /** Marks a Booking's own Slot directly (a fixture shortcut; 064 added real Queue send-in/complete endpoints, exercised in QueuePositionShrinksTest). */
    protected void setSlotStatus(Booking booking, SlotStatus status) {
        var slot = booking.getSlot();
        slot.setStatus(status);
        slotRepository.save(slot);
    }
}
