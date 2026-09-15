package com.cms.waitlist;

import com.cms.inbox.InboxItemService;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 032 research.md R5: the shared "release and re-offer" core behind both an explicit decline
 * ({@link WaitlistClaimService#decline}) and the automatic expiry sweep
 * ({@link WaitlistExpirySweepService}) - the spec's own framing describes these as one
 * mechanism with two triggers, not two mechanisms (Constitution II).
 */
@Service
public class WaitlistReleaseService {

    private final WaitlistEntryRepository waitlistEntryRepository;
    private final WaitlistMatchingService waitlistMatchingService;
    private final InboxItemService inboxItemService;

    public WaitlistReleaseService(
            WaitlistEntryRepository waitlistEntryRepository,
            WaitlistMatchingService waitlistMatchingService,
            InboxItemService inboxItemService) {
        this.waitlistEntryRepository = waitlistEntryRepository;
        this.waitlistMatchingService = waitlistMatchingService;
        this.inboxItemService = inboxItemService;
    }

    /**
     * FR-006/FR-007: releases an OFFERED entry (data-layer-guarded, Constitution IV) and, only
     * if that transition actually won, re-runs matching against the same Slot's Session for the
     * next-longest-waiting eligible entry (FR-008). A lost race (entry already resolved by a
     * concurrent claim/decline/expiry) is a silent no-op, not an error.
     */
    @Transactional
    public void release(WaitlistEntry entry) {
        int updated = waitlistEntryRepository.expireIfOffered(entry.getId());
        if (updated == 0) {
            return;
        }

        entry.expire();

        // 038-unified-realtime-inbox FR-013: auto-resolves the offer's Inbox Item - covers both
        // the decline path and the expiry-sweep path, since both funnel through this method
        // (research.md R7).
        inboxItemService.resolveByWaitlistEntry(entry.getId());

        Slot slot = entry.getOfferedSlot();
        Session session = slot.getSession();
        waitlistMatchingService.matchAndOffer(session, slot);
    }

    /**
     * 032 research.md R4: used only by {@link WaitlistClaimService#claim}'s failure pivot.
     * {@code REQUIRES_NEW} is load-bearing here, not stylistic - the caller's own transaction is
     * already doomed to roll back (a participating {@code PatientBookingService.bookSlot} call
     * just threw out of its own transactional boundary, which marks that shared transaction
     * rollback-only regardless of whether the caller subsequently catches the exception). Without
     * a genuinely independent transaction, this release-and-re-offer would silently vanish along
     * with the failed claim instead of surviving it. Re-loads the entry by id rather than reusing
     * the caller's managed reference, since that reference belongs to the doomed transaction's own
     * persistence context.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseById(UUID entryId) {
        waitlistEntryRepository.findById(entryId).ifPresent(this::release);
    }
}
