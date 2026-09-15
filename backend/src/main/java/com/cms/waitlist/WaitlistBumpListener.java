package com.cms.waitlist;

import com.cms.booking.BookingCancelledEvent;
import com.cms.scheduling.ScheduleMode;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 031 FR-009: the sole consumer of 025's {@link BookingCancelledEvent} - AFTER_COMMIT so this
 * never reacts to a cancellation that was ultimately rolled back, mirroring 037's
 * {@code NotificationDeliveryListener} (research.md R3). The Fixed-Time-mode check below is
 * defensive, not load-bearing - {@code BookingCancellationService.cancel} (025) already
 * rejects any non-Fixed-Time Session before ever publishing this event, so this branch is
 * structurally unreachable today, kept only as cheap defense-in-depth.
 */
@Component
public class WaitlistBumpListener {

    private final SlotRepository slotRepository;
    private final WaitlistMatchingService waitlistMatchingService;

    public WaitlistBumpListener(SlotRepository slotRepository, WaitlistMatchingService waitlistMatchingService) {
        this.slotRepository = slotRepository;
        this.waitlistMatchingService = waitlistMatchingService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCancelled(BookingCancelledEvent event) {
        Slot slot = slotRepository.findById(event.slotId()).orElse(null);
        if (slot == null) {
            return;
        }

        Session session = slot.getSession();
        if (session.getMode() != ScheduleMode.FIXED_TIME) {
            return;
        }

        waitlistMatchingService.matchAndOffer(session, slot);
    }
}
