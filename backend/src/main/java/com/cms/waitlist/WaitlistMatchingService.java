package com.cms.waitlist;

import com.cms.inbox.InboxItemService;
import com.cms.notification.NotificationEventService;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotStatus;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 031 FR-004..FR-008: the sole waitlist-matching entry point (FR-009 - never called from
 * anywhere but {@link WaitlistBumpListener}). Tier 1 (doctor-match) is tried first; tier 2
 * (specialization-only) is tried only if tier 1 has no eligible entry (research.md R4) - a
 * miss in both tiers is a silent no-op, not an error (FR-007).
 */
@Service
public class WaitlistMatchingService {

    private static final String EVENT_TYPE = "WAITLIST_OFFER";

    private final WaitlistEntryRepository waitlistEntryRepository;
    private final NotificationEventService notificationEventService;
    private final InboxItemService inboxItemService;

    public WaitlistMatchingService(
            WaitlistEntryRepository waitlistEntryRepository,
            NotificationEventService notificationEventService,
            InboxItemService inboxItemService) {
        this.waitlistEntryRepository = waitlistEntryRepository;
        this.notificationEventService = notificationEventService;
        this.inboxItemService = inboxItemService;
    }

    @Transactional
    public void matchAndOffer(Session session, Slot slot) {
        // 032 research.md R10: only ever offer a still-OPEN Slot - a pure no-op for 031's own
        // call path (BookingCancellationService just set it OPEN), but load-bearing for 032's
        // reuse when re-matching after a claim lost the race to an ordinary booking.
        if (slot.getStatus() != SlotStatus.OPEN) {
            return;
        }

        Optional<WaitlistEntry> tier1 = waitlistEntryRepository
                .findFirstByClinic_IdAndDoctorProfile_IdAndStatusOrderByJoinedAtAsc(
                        session.getClinic().getId(), session.getDoctorProfile().getId(), WaitlistEntryStatus.WAITING);

        WaitlistEntry match = tier1.orElseGet(() -> waitlistEntryRepository
                .findFirstByClinic_IdAndSpecializationAndDoctorProfileIsNullAndStatusOrderByJoinedAtAsc(
                        session.getClinic().getId(),
                        session.getDoctorProfile().getSpecialization(),
                        WaitlistEntryStatus.WAITING)
                .orElse(null));

        if (match == null) {
            return;
        }

        Instant now = Instant.now();
        Instant offerExpiresAt = now.plusSeconds(30 * 60);
        int updated = waitlistEntryRepository.offerIfWaiting(match.getId(), now, offerExpiresAt, slot);
        if (updated == 0) {
            // Lost a race to a concurrent WaitlistBumpListener invocation that already offered
            // this exact entry (e.g. two individual cancellations for the same doctor resolving
            // at nearly the same time) - not an error, this cancellation's slot simply stays
            // open (Constitution IV; mirrors 026/027's identical "lost race -> skip" precedent).
            return;
        }

        // Keeps this in-memory entity consistent with offerIfWaiting's DB-level change, which
        // bypassed the persistence context (mirrors Booking.setStatus's own documented purpose).
        match.offer(now, slot);

        notificationEventService.publish(
                match.getPatientAccount().getId(),
                EVENT_TYPE,
                "{\"waitlistEntryId\":\"" + match.getId() + "\",\"slotId\":\"" + slot.getId() + "\"}",
                match.getOfferExpiresAt());

        // 038-unified-realtime-inbox FR-003: a direct call, not an event - both modules already
        // exist in the same build (research.md R7).
        inboxItemService.createWaitlistOfferItem(session.getClinic(), match);
    }
}
