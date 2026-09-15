package com.cms.booking;

import com.cms.identity.doctor.DoctorProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** 017: a Doctor's own fallback fee, used when an Appointment Type has no override (FR-002). At most one per Doctor. */
@Entity
@Table(
        name = "doctor_default_fee",
        uniqueConstraints = @UniqueConstraint(name = "uq_doctor_default_fee_doctor_profile", columnNames = "doctor_profile_id"))
public class DoctorDefaultFee {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @OneToOne(optional = false)
    @JoinColumn(name = "doctor_profile_id", nullable = false)
    private DoctorProfile doctorProfile;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected DoctorDefaultFee() {
        // JPA
    }

    public DoctorDefaultFee(DoctorProfile doctorProfile, BigDecimal amount) {
        this.doctorProfile = doctorProfile;
        this.amount = amount;
    }

    public UUID getId() {
        return id;
    }

    public DoctorProfile getDoctorProfile() {
        return doctorProfile;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    /** 017-AppointmentTypeService.setDefaultFee(): upsert path. */
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
        this.updatedAt = Instant.now();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
