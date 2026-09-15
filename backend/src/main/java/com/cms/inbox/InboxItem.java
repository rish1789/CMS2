package com.cms.inbox;

import com.cms.booking.Booking;
import com.cms.identity.clinic.Clinic;
import com.cms.waitlist.WaitlistEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * 038: one outstanding-or-resolved staff work item, scoped to a Clinic. Exactly one of
 * {@link #booking} / {@link #waitlistEntry} / ({@link #doctorName} + {@link #cancelledBookingCount})
 * is populated, matching {@link #itemType} - enforced by the three static factories below, not a
 * DB constraint (mirrors {@code Slot}'s own type-conditional nullable columns precedent).
 * {@code booking}/{@code waitlistEntry} are live relations, not frozen summary copies (research.md
 * R4) - so 033's anonymization and 034's retention purge propagate into this entity's rendered
 * content automatically (spec FR-016).
 */
@Entity
@Table(name = "inbox_item")
public class InboxItem {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "clinic_id", nullable = false)
    private Clinic clinic;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false)
    private InboxItemType itemType;

    @ManyToOne
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne
    @JoinColumn(name = "waitlist_entry_id")
    private WaitlistEntry waitlistEntry;

    @Column(name = "doctor_name")
    private String doctorName;

    @Column(name = "cancelled_booking_count")
    private Integer cancelledBookingCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InboxItemStatus status;

    @Column(name = "claimed_by_account_id")
    private UUID claimedByAccountId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected InboxItem() {
        // JPA
    }

    private InboxItem(Clinic clinic, InboxItemType itemType) {
        this.clinic = clinic;
        this.itemType = itemType;
        this.status = InboxItemStatus.UNCLAIMED;
    }

    /** FR-002: fed by {@code WalkInInsertionService} once the walk-in Booking is created. */
    public static InboxItem forWalkIn(Clinic clinic, Booking booking) {
        InboxItem item = new InboxItem(clinic, InboxItemType.WALK_IN);
        item.booking = booking;
        return item;
    }

    /** FR-003: fed by {@code WaitlistMatchingService} once an offer is made. */
    public static InboxItem forWaitlistOffer(Clinic clinic, WaitlistEntry waitlistEntry) {
        InboxItem item = new InboxItem(clinic, InboxItemType.WAITLIST_OFFER);
        item.waitlistEntry = waitlistEntry;
        return item;
    }

    /**
     * FR-004: fed by {@code DeVerificationCascadeService}, one per affected clinic. Frozen fields
     * (not a live relation) since Doctor identity is never anonymized/purged (research.md R4).
     */
    public static InboxItem forCascadeNotice(Clinic clinic, String doctorName, int cancelledBookingCount) {
        InboxItem item = new InboxItem(clinic, InboxItemType.DEVERIFICATION_CASCADE);
        item.doctorName = doctorName;
        item.cancelledBookingCount = cancelledBookingCount;
        return item;
    }

    public UUID getId() {
        return id;
    }

    public Clinic getClinic() {
        return clinic;
    }

    public InboxItemType getItemType() {
        return itemType;
    }

    public Booking getBooking() {
        return booking;
    }

    public WaitlistEntry getWaitlistEntry() {
        return waitlistEntry;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public Integer getCancelledBookingCount() {
        return cancelledBookingCount;
    }

    public InboxItemStatus getStatus() {
        return status;
    }

    public UUID getClaimedByAccountId() {
        return claimedByAccountId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Keeps this in-memory entity consistent after a data-layer-guarded {@code @Modifying} update
     * has already confirmed the transition (mirrors {@code Booking.setStatus}'s documented
     * purpose) - not a second place any transition is decided.
     */
    public void syncClaimed(UUID accountId) {
        this.status = InboxItemStatus.CLAIMED;
        this.claimedByAccountId = accountId;
    }

    public void syncUnclaimed() {
        this.status = InboxItemStatus.UNCLAIMED;
        this.claimedByAccountId = null;
    }

    public void syncResolved() {
        this.status = InboxItemStatus.RESOLVED;
    }
}
