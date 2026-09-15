package com.cms.patient.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * The patient's global, self-service login identity - entirely separate from the staff
 * {@code Account} entity (001): no foreign key, no shared uniqueness constraint
 * (FR-004, FR-008). {@code email} is unique among Patient Accounts only, enforced here
 * via a {@code @Table} unique constraint (closes the concurrent-signup race at the data
 * layer, not just in application code - Constitution Principle IV).
 */
@Entity
@Table(
        name = "patient_account",
        uniqueConstraints = @UniqueConstraint(name = "uq_patient_account_email", columnNames = "email"))
public class PatientAccount {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column private String mobile;

    @Column(name = "notification_opt_in", nullable = false)
    private boolean notificationOptIn = true;

    /** 011-notification-event-pipeline: independent of {@link #smsOptIn} and of the legacy {@link #notificationOptIn}. */
    @Column(name = "push_opt_in", nullable = false)
    private boolean pushOptIn = true;

    /** 011-notification-event-pipeline: independent of {@link #pushOptIn} and of the legacy {@link #notificationOptIn}. */
    @Column(name = "sms_opt_in", nullable = false)
    private boolean smsOptIn = true;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected PatientAccount() {
        // JPA
    }

    public PatientAccount(String email, String passwordHash, String mobile) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.mobile = mobile;
        this.notificationOptIn = true;
        this.active = true;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getMobile() {
        return mobile;
    }

    public boolean isNotificationOptIn() {
        return notificationOptIn;
    }

    public boolean isPushOptIn() {
        return pushOptIn;
    }

    /** 011-notification-event-pipeline: infrastructure for a future settings feature; this feature itself has no UI/endpoint for it. */
    public void setPushOptIn(boolean pushOptIn) {
        this.pushOptIn = pushOptIn;
    }

    public boolean isSmsOptIn() {
        return smsOptIn;
    }

    /** 011-notification-event-pipeline: infrastructure for a future settings feature; this feature itself has no UI/endpoint for it. */
    public void setSmsOptIn(boolean smsOptIn) {
        this.smsOptIn = smsOptIn;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
