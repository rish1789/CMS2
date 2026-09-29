package com.cms.scheduling.domain;

import com.cms.scheduling.service.QueueSlotService;


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

    /** 063-front-desk-walk-in (FR-014): when the patient was sent in - stamped on entering APPEARED. */
    @Column(name = "appeared_at")
    private Instant appearedAt;

    /** 063-front-desk-walk-in (FR-014): when the visit finished - stamped on entering COMPLETED. */
    @Column(name = "completed_at")
    private Instant completedAt;

    protected Slot() {
        // JPA
    }

    /** Fixed-Time constructor (012) - {@code tokenNumber} stays null. */
    public Slot(Session session, LocalTime startTime, LocalTime endTime) {
        this.session = session;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = SlotStatus.OPEN;
    }

    /** Queue/Token constructor (019) - {@code startTime}/{@code endTime} stay null/null. */
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

    public Integer getTokenNumber() {
        return tokenNumber;
    }

    public SlotStatus getStatus() {
        return status;
    }

    /**
     * 020-staff-assisted-fixed-time-booking: flips OPEN to BOOKED when a Booking is created for this Slot.
     *
     * <p>063-front-desk-walk-in (FR-014): entering APPEARED stamps {@link #appearedAt} and entering
     * COMPLETED stamps {@link #completedAt}. The stamp lives on the transition itself, so every path
     * that sends a patient in or completes a visit (manual, or the auto-completion sweep) records it.
     */
    public void setStatus(SlotStatus status) {
        if (status == SlotStatus.APPEARED && this.status != SlotStatus.APPEARED) {
            this.appearedAt = Instant.now();
        }
        if (status == SlotStatus.COMPLETED && this.status != SlotStatus.COMPLETED) {
            this.completedAt = Instant.now();
        }
        this.status = status;
    }

    public Instant getAppearedAt() {
        return appearedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    /**
     * 063-front-desk-walk-in (research.md Decisions 1 and 3): no scheduled time - a Queue token, or
     * a walk-in waiting in a Fixed-Time session's walk-in line. Every place that builds a time from
     * {@link #getStartTime()} must check this first.
     */
    public boolean isUntimed() {
        return startTime == null;
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
