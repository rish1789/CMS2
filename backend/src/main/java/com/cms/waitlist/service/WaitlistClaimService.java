package com.cms.waitlist.service;

import com.cms.waitlist.api.PatientWaitlistClaimController;
import com.cms.waitlist.domain.WaitlistEntry;
import com.cms.waitlist.domain.WaitlistEntryStatus;
import com.cms.waitlist.exception.WaitlistEntryNotFoundException;
import com.cms.waitlist.exception.WaitlistOfferNotClaimableException;
import com.cms.waitlist.repository.WaitlistEntryRepository;


import com.cms.booking.domain.Booking;
import com.cms.booking.service.PatientBookingService;
import com.cms.booking.exception.SlotAlreadyBookedException;
import com.cms.inbox.service.InboxItemService;
import com.cms.waitlist.dto.ClaimWaitlistRequest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 032: the two patient-self-service actions on an {@code OFFERED} entry - claim (research.md
 * R2/R4) and decline (research.md R5, delegating to {@link WaitlistReleaseService}). No
 * authorization here beyond ownership - that's {@link PatientWaitlistClaimController}'s job,
 * mirroring 028's {@code BookingCancellationService} split.
 */
@Service
public class WaitlistClaimService {

    private final WaitlistEntryRepository waitlistEntryRepository;
    private final PatientBookingService patientBookingService;
    private final WaitlistReleaseService waitlistReleaseService;
    private final InboxItemService inboxItemService;

    public WaitlistClaimService(
            WaitlistEntryRepository waitlistEntryRepository,
            PatientBookingService patientBookingService,
            WaitlistReleaseService waitlistReleaseService,
            InboxItemService inboxItemService) {
        this.waitlistEntryRepository = waitlistEntryRepository;
        this.patientBookingService = patientBookingService;
        this.waitlistReleaseService = waitlistReleaseService;
        this.inboxItemService = inboxItemService;
    }

    /**
     * FR-001..FR-004a (research.md R4): guard-claims the entry first (data-layer race-closure,
     * Constitution IV), then attempts the actual booking via {@link PatientBookingService}. If
     * the offered Slot was already taken through the ordinary booking flow in the meantime, the
     * entry is pivoted to {@code EXPIRED} and the next eligible entry is offered the same Slot
     * (a safe no-op per research.md R10, since the Slot is no longer OPEN) via
     * {@link WaitlistReleaseService#releaseById}, whose {@code REQUIRES_NEW} transaction is
     * load-bearing here: {@code bookSlot}'s own thrown exception has already marked this
     * (participating) transaction rollback-only, so a plain in-line write here would silently
     * vanish along with the failed claim once this method re-throws.
     *
     * <p>The release must run only after this transaction has rolled back: {@code claimIfOffered}
     * above holds the entry's row lock until then, so calling {@code releaseById} in-line made its
     * {@code REQUIRES_NEW} update wait on a lock held by its own thread - a self-deadlock that hung
     * the request forever. It is therefore deferred to {@code afterCompletion}.
     */
    @Transactional
    public Booking claim(UUID entryId, UUID patientAccountId, ClaimWaitlistRequest request) {
        WaitlistEntry entry = findOwned(entryId, patientAccountId);

        int updated = waitlistEntryRepository.claimIfOffered(entryId, Instant.now());
        if (updated == 0) {
            throw new WaitlistOfferNotClaimableException(entryId);
        }
        entry.markClaimed();

        Booking booking;
        try {
            booking = patientBookingService.bookSlot(
                    patientAccountId,
                    entry.getClinic().getId(),
                    entry.getOfferedSlot().getId(),
                    new PatientBookingService.BookSlotInput(request.patientName(), request.appointmentTypeId()));
        } catch (SlotAlreadyBookedException e) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    waitlistReleaseService.releaseById(entryId);
                }
            });
            throw e;
        }

        // 038-unified-realtime-inbox FR-013: auto-resolves the offer's Inbox Item only once the
        // claim has actually succeeded end-to-end - placed after bookSlot (not before, alongside
        // claimIfOffered) so a failed claim that pivots to release-and-reoffer above rolls this
        // back too, instead of leaving the Inbox Item stuck RESOLVED while the WaitlistEntry
        // itself gets EXPIRED by an independent REQUIRES_NEW transaction (the same
        // rollback-poisoning bug class 029/033 already found this session, research.md R7).
        inboxItemService.resolveByWaitlistEntry(entryId);

        return booking;
    }

    /** FR-005/FR-006: ownership-scoped, then delegates to the shared release core. */
    @Transactional
    public WaitlistEntry decline(UUID entryId, UUID patientAccountId) {
        WaitlistEntry entry = findOwned(entryId, patientAccountId);
        if (entry.getStatus() != WaitlistEntryStatus.OFFERED) {
            throw new WaitlistOfferNotClaimableException(entryId);
        }
        // The status check above reads a snapshot; a concurrent claim can still win the guarded
        // update inside release(). Losing it must be reported, not treated as a successful decline
        // (032 FR-010/SC-004: exactly one of concurrent claim/decline/expiry succeeds).
        if (!waitlistReleaseService.release(entry)) {
            throw new WaitlistOfferNotClaimableException(entryId);
        }
        return entry;
    }

    /** research.md R8: ownership (not clinic membership) is the access boundary - never distinguishes "doesn't exist" from "not yours." */
    private WaitlistEntry findOwned(UUID entryId, UUID patientAccountId) {
        WaitlistEntry entry = waitlistEntryRepository
                .findById(entryId)
                .orElseThrow(() -> new WaitlistEntryNotFoundException(entryId));
        if (!entry.getPatientAccount().getId().equals(patientAccountId)) {
            throw new WaitlistEntryNotFoundException(entryId);
        }
        return entry;
    }
}
