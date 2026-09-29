package com.cms.scheduling.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "session_cancellation")
public class SessionCancellation {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Column(name = "from_time")
    private LocalTime fromTime;

    @Column(name = "to_time")
    private LocalTime toTime;

    @Column(name = "cancelled_at", nullable = false)
    private Instant cancelledAt;

    @Column(name = "cancelled_by_account_id", nullable = false)
    private UUID cancelledByAccountId;

    protected SessionCancellation() {
        // JPA
    }

    private SessionCancellation(Session session, LocalTime fromTime, LocalTime toTime, UUID cancelledByAccountId, Instant cancelledAt) {
        this.session = session;
        this.fromTime = fromTime;
        this.toTime = toTime;
        this.cancelledByAccountId = cancelledByAccountId;
        this.cancelledAt = cancelledAt;
    }

    public static SessionCancellation whole(Session session, UUID accountId, Instant now) {
        return new SessionCancellation(session, null, null, accountId, now);
    }

    public static SessionCancellation range(Session session, LocalTime from, LocalTime to, UUID accountId, Instant now) {
        return new SessionCancellation(session, from, to, accountId, now);
    }

    public UUID getId() {
        return id;
    }

    public Session getSession() {
        return session;
    }

    public LocalTime getFromTime() {
        return fromTime;
    }

    public LocalTime getToTime() {
        return toTime;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public UUID getCancelledByAccountId() {
        return cancelledByAccountId;
    }
}
