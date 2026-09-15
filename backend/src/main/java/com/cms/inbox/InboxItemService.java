package com.cms.inbox;

import com.cms.booking.Booking;
import com.cms.identity.account.Account;
import com.cms.identity.account.AccountRepository;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.identity.clinic.Clinic;
import com.cms.inbox.dto.InboxItemResponse;
import com.cms.waitlist.WaitlistEntry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 038: create/claim/release/resolve operations for {@link InboxItem}, all @Transactional. Every
 * state-transition method broadcasts the resulting item to the owning clinic's live viewers
 * (research.md R1) after the data-layer-guarded update actually wins (research.md R8).
 */
@Service
public class InboxItemService {

    private static final List<RoleAssignment.Role> FRONT_DESK_ROLES =
            List.of(RoleAssignment.Role.Operations, RoleAssignment.Role.ClinicAdmin);

    private final InboxItemRepository inboxItemRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final AccountRepository accountRepository;
    private final InboxBroadcastService inboxBroadcastService;

    public InboxItemService(
            InboxItemRepository inboxItemRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            AccountRepository accountRepository,
            InboxBroadcastService inboxBroadcastService) {
        this.inboxItemRepository = inboxItemRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.accountRepository = accountRepository;
        this.inboxBroadcastService = inboxBroadcastService;
    }

    /** FR-002: fed by {@code WalkInInsertionService} once the walk-in Booking is created. */
    @Transactional
    public void createWalkInItem(Clinic clinic, Booking booking) {
        InboxItem item = inboxItemRepository.save(InboxItem.forWalkIn(clinic, booking));
        broadcast(clinic.getId(), item);
    }

    /** FR-003: fed by {@code WaitlistMatchingService} once an offer is made. */
    @Transactional
    public void createWaitlistOfferItem(Clinic clinic, WaitlistEntry waitlistEntry) {
        InboxItem item = inboxItemRepository.save(InboxItem.forWaitlistOffer(clinic, waitlistEntry));
        broadcast(clinic.getId(), item);
    }

    /** FR-004: fed by {@code DeVerificationCascadeService}, one item per affected clinic (research.md R7). */
    @Transactional
    public void createCascadeNotices(String doctorName, Map<UUID, List<Booking>> cancelledBookingsByClinic) {
        cancelledBookingsByClinic.forEach((clinicId, bookings) -> {
            if (bookings.isEmpty()) {
                return;
            }
            Clinic clinic = bookings.get(0).getSlot().getSession().getClinic();
            InboxItem item =
                    inboxItemRepository.save(InboxItem.forCascadeNotice(clinic, doctorName, bookings.size()));
            broadcast(clinicId, item);
        });
    }

    /** FR-006: every non-RESOLVED item at this clinic, oldest-unclaimed-first, for an authorized front-desk staff caller. */
    @Transactional(readOnly = true)
    public List<InboxItemResponse> list(UUID clinicId, UUID callerAccountId) {
        requireAuthorized(callerAccountId, clinicId);
        return inboxItemRepository.findByClinic_IdAndStatusNotOrderByCreatedAtAsc(clinicId, InboxItemStatus.RESOLVED)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** FR-007/FR-008: claims an unclaimed item; throws {@link AlreadyClaimedException} on a lost race or an already-claimed/resolved item. */
    @Transactional
    public InboxItemResponse claim(UUID clinicId, UUID itemId, UUID callerAccountId) {
        requireAuthorized(callerAccountId, clinicId);
        InboxItem item = findAtClinic(clinicId, itemId);
        int updated = inboxItemRepository.claimIfUnclaimed(itemId, callerAccountId);
        if (updated == 0) {
            throw new AlreadyClaimedException(itemId);
        }
        item.syncClaimed(callerAccountId);
        return broadcastAndReturn(clinicId, item);
    }

    /** FR-010: only the current claimant may release. */
    @Transactional
    public InboxItemResponse release(UUID clinicId, UUID itemId, UUID callerAccountId) {
        requireAuthorized(callerAccountId, clinicId);
        InboxItem item = findAtClinic(clinicId, itemId);
        int updated = inboxItemRepository.releaseIfClaimedBy(itemId, callerAccountId);
        if (updated == 0) {
            throw new NotClaimantException(itemId);
        }
        item.syncUnclaimed();
        return broadcastAndReturn(clinicId, item);
    }

    /** FR-011: only the current claimant may resolve. */
    @Transactional
    public InboxItemResponse resolve(UUID clinicId, UUID itemId, UUID callerAccountId) {
        requireAuthorized(callerAccountId, clinicId);
        InboxItem item = findAtClinic(clinicId, itemId);
        int updated = inboxItemRepository.resolveIfClaimedBy(itemId, callerAccountId);
        if (updated == 0) {
            throw new NotClaimantException(itemId);
        }
        item.syncResolved();
        return broadcastAndReturn(clinicId, item);
    }

    /**
     * FR-013/analyze finding F1: the waitlist-lifecycle auto-resolve path (claim/decline/expiry) -
     * independent of claim state, data-layer-guarded against a concurrent staff {@code resolve} on
     * the same item. A lost race (already resolved by the other path) is a silent no-op.
     */
    @Transactional
    public void resolveByWaitlistEntry(UUID waitlistEntryId) {
        int updated = inboxItemRepository.resolveIfNotResolved(waitlistEntryId);
        if (updated == 0) {
            return;
        }
        inboxItemRepository.findByWaitlistEntry_Id(waitlistEntryId).ifPresent(item -> {
            item.syncResolved();
            broadcast(item.getClinic().getId(), item);
        });
    }

    private InboxItem findAtClinic(UUID clinicId, UUID itemId) {
        return inboxItemRepository
                .findById(itemId)
                .filter(i -> i.getClinic().getId().equals(clinicId))
                .orElseThrow(() -> new InboxItemNotFoundException(itemId));
    }

    /**
     * Mirrors 020/025's requireAuthorized: an active Operations or ClinicAdmin at this clinic -
     * never the Doctor (research.md R6). Package-visible so {@link InboxController} can reuse it
     * for the SSE stream endpoint, which has no other InboxItemService call to route through.
     */
    void requireAuthorized(UUID callerAccountId, UUID clinicId) {
        boolean authorized = roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleInAndActiveTrue(
                callerAccountId, clinicId, FRONT_DESK_ROLES);
        if (!authorized) {
            throw new ForbiddenException();
        }
    }

    private InboxItemResponse broadcastAndReturn(UUID clinicId, InboxItem item) {
        InboxItemResponse response = toResponse(item);
        inboxBroadcastService.broadcast(clinicId, response);
        return response;
    }

    private void broadcast(UUID clinicId, InboxItem item) {
        inboxBroadcastService.broadcast(clinicId, toResponse(item));
    }

    private InboxItemResponse toResponse(InboxItem item) {
        String claimedByName = item.getClaimedByAccountId() == null
                ? null
                : accountRepository.findById(item.getClaimedByAccountId()).map(Account::getName).orElse(null);
        return InboxItemResponse.of(item, claimedByName);
    }
}
