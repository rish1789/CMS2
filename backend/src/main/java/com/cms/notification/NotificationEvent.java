package com.cms.notification;

import com.cms.patient.account.PatientAccount;
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
 * 011: one occurrence of "something happened, the patient should potentially be told."
 * {@code pushEligible}/{@code smsEligible} are a snapshot of the patient's opt-in state
 * at publish time (FR-002) - never recomputed later. {@code eventType} is deliberately an
 * opaque, caller-supplied String, not a fixed enum (research.md) - no event-producing
 * feature exists yet to define one.
 */
@Entity
@Table(name = "notification_event")
public class NotificationEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_account_id", nullable = false)
    private PatientAccount patientAccount;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column
    private String payload;

    @Column(name = "push_eligible", nullable = false)
    private boolean pushEligible;

    @Column(name = "sms_eligible", nullable = false)
    private boolean smsEligible;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationEventStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected NotificationEvent() {
        // JPA
    }

    public NotificationEvent(
            PatientAccount patientAccount,
            String eventType,
            String payload,
            boolean pushEligible,
            boolean smsEligible,
            Instant expiresAt) {
        this.patientAccount = patientAccount;
        this.eventType = eventType;
        this.payload = payload;
        this.pushEligible = pushEligible;
        this.smsEligible = smsEligible;
        this.expiresAt = expiresAt;
        this.status = NotificationEventStatus.PENDING;
    }

    public UUID getId() {
        return id;
    }

    public PatientAccount getPatientAccount() {
        return patientAccount;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public boolean isPushEligible() {
        return pushEligible;
    }

    public boolean isSmsEligible() {
        return smsEligible;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public NotificationEventStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
