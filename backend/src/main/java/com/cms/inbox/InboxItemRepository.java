package com.cms.inbox;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InboxItemRepository extends JpaRepository<InboxItem, UUID> {

    /** FR-006/FR-012: every non-RESOLVED item at this clinic, oldest-unclaimed-first (spec Assumptions). */
    List<InboxItem> findByClinic_IdAndStatusNotOrderByCreatedAtAsc(UUID clinicId, InboxItemStatus status);

    /** FR-013: the auto-resolve trigger's lookup - at most one live item per WaitlistEntry (data-model.md). */
    Optional<InboxItem> findByWaitlistEntry_Id(UUID waitlistEntryId);

    /**
     * research.md R8/analyze finding F1: the actual concurrency guarantee for claiming - a
     * data-layer-guarded conditional update, not a plain read-then-write, mirroring
     * {@code WaitlistEntryRepository.offerIfWaiting}. Returns 0 if the item was already
     * CLAIMED/RESOLVED.
     */
    @Modifying
    @Query("UPDATE InboxItem i SET i.status = com.cms.inbox.InboxItemStatus.CLAIMED, i.claimedByAccountId = :accountId "
            + "WHERE i.id = :id AND i.status = com.cms.inbox.InboxItemStatus.UNCLAIMED")
    int claimIfUnclaimed(@Param("id") UUID id, @Param("accountId") UUID accountId);

    /** FR-010: only the current claimant may release. Returns 0 if not CLAIMED, or claimed by someone else. */
    @Modifying
    @Query("UPDATE InboxItem i SET i.status = com.cms.inbox.InboxItemStatus.UNCLAIMED, i.claimedByAccountId = null "
            + "WHERE i.id = :id AND i.status = com.cms.inbox.InboxItemStatus.CLAIMED AND i.claimedByAccountId = :accountId")
    int releaseIfClaimedBy(@Param("id") UUID id, @Param("accountId") UUID accountId);

    /** FR-011: only the current claimant may resolve. Returns 0 if not CLAIMED, or claimed by someone else. */
    @Modifying
    @Query("UPDATE InboxItem i SET i.status = com.cms.inbox.InboxItemStatus.RESOLVED "
            + "WHERE i.id = :id AND i.status = com.cms.inbox.InboxItemStatus.CLAIMED AND i.claimedByAccountId = :accountId")
    int resolveIfClaimedBy(@Param("id") UUID id, @Param("accountId") UUID accountId);

    /**
     * FR-013/analyze finding F1: the waitlist-lifecycle auto-resolve path - independent of claim
     * state, but still data-layer-guarded against a concurrent staff-initiated resolve on the same
     * item. Returns 0 if already RESOLVED (a silent no-op, mirrors 026/027's lost-race precedent).
     */
    @Modifying
    @Query("UPDATE InboxItem i SET i.status = com.cms.inbox.InboxItemStatus.RESOLVED "
            + "WHERE i.waitlistEntry.id = :waitlistEntryId AND i.status <> com.cms.inbox.InboxItemStatus.RESOLVED")
    int resolveIfNotResolved(@Param("waitlistEntryId") UUID waitlistEntryId);

    /**
     * super-admin-console-redesign: permanent-delete's cascade cleanup for a clinic that passed
     * its activity gate - only ever reached when this is expected to already be empty, kept as a
     * defensive delete rather than assumed.
     */
    long deleteByClinic_Id(UUID clinicId);
}
