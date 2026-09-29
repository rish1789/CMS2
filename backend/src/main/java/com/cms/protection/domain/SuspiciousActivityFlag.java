package com.cms.protection.domain;

import com.cms.identity.clinic.Clinic;
import com.cms.patient.account.domain.PatientAccount;
import com.cms.protection.exception.FlagAlreadyResolvedException;
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
 * 060-booking-abuse-prevention (spec.md FR-014, FR-021-FR-025, Clarifications): one row per
 * triggered signal episode for a patient. {@code clinic} is null only for the single documented
 * cross-clinic case (FR-022's global-limit fact) - every other signal type always sets it. Never
 * carries a numeric "risk score" - each row is one independent piece of evidence for a human to
 * review (BR-003). At most one OUTSTANDING row exists per (patient, clinic, signalType) at a time
 * (enforced by a partial unique index, not only in application code - see V37 migration).
 */
@Entity
@Table(name = "suspicious_activity_flag")
public class SuspiciousActivityFlag {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_account_id", nullable = false)
    private PatientAccount patientAccount;

    @ManyToOne
    @JoinColumn(name = "clinic_id")
    private Clinic clinic;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false)
    private SuspiciousActivitySignalType signalType;

    @Column(nullable = false)
    private String reason;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SuspiciousActivityFlagStatus status;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private String resolvedBy;

    protected SuspiciousActivityFlag() {
        // JPA
    }

    public SuspiciousActivityFlag(
            PatientAccount patientAccount,
            Clinic clinic,
            SuspiciousActivitySignalType signalType,
            String reason,
            Instant detectedAt) {
        this.patientAccount = patientAccount;
        this.clinic = clinic;
        this.signalType = signalType;
        this.reason = reason;
        this.detectedAt = detectedAt;
        this.status = SuspiciousActivityFlagStatus.OUTSTANDING;
    }

    public UUID getId() {
        return id;
    }

    public PatientAccount getPatientAccount() {
        return patientAccount;
    }

    public Clinic getClinic() {
        return clinic;
    }

    public SuspiciousActivitySignalType getSignalType() {
        return signalType;
    }

    public String getReason() {
        return reason;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public SuspiciousActivityFlagStatus getStatus() {
        return status;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    /** FR-023: one-way OUTSTANDING -> RESOLVED transition. */
    public void resolve(Instant resolvedAt, String resolvedBy) {
        if (status == SuspiciousActivityFlagStatus.RESOLVED) {
            throw new FlagAlreadyResolvedException(id);
        }
        this.status = SuspiciousActivityFlagStatus.RESOLVED;
        this.resolvedAt = resolvedAt;
        this.resolvedBy = resolvedBy;
    }
}
