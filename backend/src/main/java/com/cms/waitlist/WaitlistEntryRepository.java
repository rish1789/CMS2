package com.cms.waitlist;

import com.cms.scheduling.Slot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, UUID> {

    /** research.md R4 tier 1: the longest-waiting WAITING entry matched to this exact doctor. */
    Optional<WaitlistEntry> findFirstByClinic_IdAndDoctorProfile_IdAndStatusOrderByJoinedAtAsc(
            UUID clinicId, UUID doctorProfileId, WaitlistEntryStatus status);

    /** research.md R4 tier 2: the longest-waiting WAITING entry matched only to this specialization, no doctor preference. */
    Optional<WaitlistEntry> findFirstByClinic_IdAndSpecializationAndDoctorProfileIsNullAndStatusOrderByJoinedAtAsc(
            UUID clinicId, String specialization, WaitlistEntryStatus status);

    /**
     * Convergence (Constitution Principle IV): the actual concurrency guarantee for matching - a
     * data-layer-guarded conditional update, not a plain read-then-write, mirroring
     * {@code BookingRepository.cancelIfActive}. Returns 0 if the entry was already OFFERED
     * (including a lost race against a concurrent {@code WaitlistBumpListener} invocation).
     * 032: also records which Slot this offer refers to (data-model.md).
     */
    @Modifying
    @Query("UPDATE WaitlistEntry w SET w.status = com.cms.waitlist.WaitlistEntryStatus.OFFERED, "
            + "w.offeredAt = :offeredAt, w.offerExpiresAt = :offerExpiresAt, w.offeredSlot = :slot "
            + "WHERE w.id = :id AND w.status = com.cms.waitlist.WaitlistEntryStatus.WAITING")
    int offerIfWaiting(
            @Param("id") UUID id,
            @Param("offeredAt") Instant offeredAt,
            @Param("offerExpiresAt") Instant offerExpiresAt,
            @Param("slot") Slot slot);

    /**
     * 032 research.md R4: the concurrency guarantee for claiming - only succeeds while the entry
     * is still OFFERED and its window hasn't lapsed. Returns 0 if already resolved by a
     * concurrent claim/decline/expiry, or if the window already passed (FR-004/FR-010).
     */
    @Modifying
    @Query("UPDATE WaitlistEntry w SET w.status = com.cms.waitlist.WaitlistEntryStatus.CLAIMED "
            + "WHERE w.id = :id AND w.status = com.cms.waitlist.WaitlistEntryStatus.OFFERED "
            + "AND w.offerExpiresAt > :now")
    int claimIfOffered(@Param("id") UUID id, @Param("now") Instant now);

    /**
     * 032 research.md R5: the shared concurrency guarantee behind both decline and the expiry
     * sweep. Returns 0 if the entry is no longer OFFERED (already claimed, already resolved by a
     * concurrent decline/expiry).
     */
    @Modifying
    @Query("UPDATE WaitlistEntry w SET w.status = com.cms.waitlist.WaitlistEntryStatus.EXPIRED "
            + "WHERE w.id = :id AND w.status = com.cms.waitlist.WaitlistEntryStatus.OFFERED")
    int expireIfOffered(@Param("id") UUID id);

    /** 032: the expiry sweep's candidate set - every currently-lapsed OFFERED entry. */
    List<WaitlistEntry> findByStatusAndOfferExpiresAtBefore(WaitlistEntryStatus status, Instant now);

    /**
     * _diagnostics [HIGH] - [WAITLIST_CLAIM] - [WORKFLOW_GAP]: lets a patient discover their own
     * entries (and the {@code entryId} of any {@code OFFERED} one) - previously the only place
     * {@code waitlistEntryId} was exposed at all was the staff-only Inbox summary.
     */
    List<WaitlistEntry> findByPatientAccount_IdOrderByJoinedAtDesc(UUID patientAccountId);

    /**
     * dashboard-live-data-2026-09-10: the clinic tools dashboard's "waitlist backlog" tile - how
     * many patients are currently WAITING at this clinic (not OFFERED/CLAIMED/EXPIRED, which have
     * already moved past "waiting"). A count, not a list - the dashboard only needs the number.
     */
    long countByClinic_IdAndStatus(UUID clinicId, WaitlistEntryStatus status);

    /** super-admin-console-redesign: the doctor permanent-delete gate - a standing waitlist entry counts as real activity. */
    long countByDoctorProfile_Id(UUID doctorProfileId);

    /**
     * super-admin-console-redesign: permanent-delete's cascade cleanup for a clinic that passed
     * its activity gate (no Patient/Schedule/Session attached) - only ever reached when this is
     * expected to already be empty, kept as a defensive delete rather than assumed.
     */
    long deleteByClinic_Id(UUID clinicId);

    /** super-admin-console-redesign: permanent-delete's cascade cleanup counterpart for a doctor profile. */
    long deleteByDoctorProfile_Id(UUID doctorProfileId);
}
