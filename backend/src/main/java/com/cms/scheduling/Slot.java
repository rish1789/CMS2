package com.cms.scheduling;

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
import java.time.LocalTime;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * 018: one bookable unit within a {@link Session}, created in the same step as the
 * Session itself for Fixed-Time mode (a time window, {@code startTime}/{@code endTime}
 * set, {@code tokenNumber} null). 019 extends this in place for Queue/Token mode
 * (created one at a time by {@link QueueSlotService}; {@code tokenNumber} set,
 * {@code startTime}/{@code endTime} null - tokens are sequential, not time-sliced).
 */
@Entity
@Table(name = "slot")
public class Slot {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "is_buffer", nullable = false)
    private boolean isBuffer;

    @Column(name = "token_number")
    private Integer tokenNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SlotStatus status;

    /** 023-no-show-detection: exempts this Slot from automatic no-show marking when set. No feature in this backlog sets it yet. */
    @Column(name = "on_hold", nullable = false)
    private boolean onHold = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Slot() {
        // JPA
    }

    /** Fixed-Time constructor (012) - {@code tokenNumber} stays null. */
    public Slot(Session session, LocalTime startTime, LocalTime endTime, boolean isBuffer) {
        this.session = session;
        this.startTime = startTime;
        this.endTime = endTime;
        this.isBuffer = isBuffer;
        this.status = SlotStatus.OPEN;
    }

    /** Queue/Token constructor (019) - {@code startTime}/{@code endTime}/{@code isBuffer} stay null/null/false. */
    public Slot(Session session, int tokenNumber) {
        this.session = session;
        this.tokenNumber = tokenNumber;
        this.status = SlotStatus.OPEN;
    }

    public UUID getId() {
        return id;
    }

    public Session getSession() {
        return session;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public boolean isBuffer() {
        return isBuffer;
    }

    public Integer getTokenNumber() {
        return tokenNumber;
    }

    public SlotStatus getStatus() {
        return status;
    }

    /** 020-staff-assisted-fixed-time-booking: flips OPEN to BOOKED when a Booking is created for this Slot. */
    public void setStatus(SlotStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isOnHold() {
        return onHold;
    }

    public void setOnHold(boolean onHold) {
        this.onHold = onHold;
    }
}
