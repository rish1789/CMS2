package com.cms.patient.record;

import com.cms.identity.clinic.Clinic;
import com.cms.patient.account.PatientAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * The clinic-scoped clinical/visit record - first defined by
 * 009-patient-record-phone-linking. {@code patientAccount} is nullable: null for a
 * walk-in record never claimed by a self-service booking, set once linked. Distinct from
 * {@link PatientAccount}, the global login identity (039) - a person has one Account but
 * one Patient row per clinic they've visited.
 */
@Entity
@Table(name = "patient")
public class Patient {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "clinic_id", nullable = false)
    private Clinic clinic;

    @OneToOne
    @JoinColumn(name = "patient_account_id")
    private PatientAccount patientAccount;

    @Column(nullable = false)
    private String name;

    @Column private String phone;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** 037: durable, queryable marker - null until {@link #anonymize()} runs; never changed again once set (idempotent, FR-007). */
    @Column(name = "anonymized_at")
    private Instant anonymizedAt;

    protected Patient() {
        // JPA
    }

    public Patient(Clinic clinic, PatientAccount patientAccount, String name, String phone) {
        this.clinic = clinic;
        this.patientAccount = patientAccount;
        this.name = name;
        this.phone = phone;
    }

    public UUID getId() {
        return id;
    }

    public Clinic getClinic() {
        return clinic;
    }

    public PatientAccount getPatientAccount() {
        return patientAccount;
    }

    /** 009 FR-003: sets the link when an unlinked walk-in record is matched and claimed. */
    public void setPatientAccount(PatientAccount patientAccount) {
        this.patientAccount = patientAccount;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getAnonymizedAt() {
        return anonymizedAt;
    }

    public boolean isAnonymized() {
        return anonymizedAt != null;
    }

    /** 037 FR-002/FR-004/FR-007: scrubs every identifying field this entity actually has. A no-op once already anonymized - idempotent, never overwrites the original timestamp. */
    public void anonymize() {
        if (isAnonymized()) {
            return;
        }
        this.name = "Anonymized Patient";
        this.phone = null;
        this.anonymizedAt = Instant.now();
    }
}
