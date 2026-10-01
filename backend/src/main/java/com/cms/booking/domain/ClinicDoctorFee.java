package com.cms.booking.domain;

import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * 068-per-clinic-fees FR-001: a Doctor's default fee at one Clinic - the fallback when the booked
 * Appointment Type has no price at that clinic. At most one per (clinic, doctor), set only by that
 * clinic's ClinicAdmin (FR-005).
 */
@Entity
@Table(
        name = "clinic_doctor_fee",
        uniqueConstraints =
                @UniqueConstraint(name = "uq_clinic_doctor_fee", columnNames = {"clinic_id", "doctor_profile_id"}))
public class ClinicDoctorFee {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "clinic_id", nullable = false)
    private Clinic clinic;

    @ManyToOne(optional = false)
    @JoinColumn(name = "doctor_profile_id", nullable = false)
    private DoctorProfile doctorProfile;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by_account_id")
    private UUID updatedByAccountId;

    protected ClinicDoctorFee() {
        // JPA
    }

    public ClinicDoctorFee(Clinic clinic, DoctorProfile doctorProfile, BigDecimal amount, UUID updatedByAccountId) {
        this.clinic = clinic;
        this.doctorProfile = doctorProfile;
        this.amount = amount;
        this.updatedByAccountId = updatedByAccountId;
    }

    public UUID getId() {
        return id;
    }

    public Clinic getClinic() {
        return clinic;
    }

    public DoctorProfile getDoctorProfile() {
        return doctorProfile;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getUpdatedByAccountId() {
        return updatedByAccountId;
    }

    /** Upsert path: changes the amount and records who changed it. */
    public void update(BigDecimal amount, UUID updatedByAccountId) {
        this.amount = amount;
        this.updatedByAccountId = updatedByAccountId;
        this.updatedAt = Instant.now();
    }
}
