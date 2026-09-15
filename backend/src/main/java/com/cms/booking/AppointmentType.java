package com.cms.booking;

import com.cms.identity.doctor.DoctorProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** 017: a Doctor-scoped named category of appointment, with an optional fee override (FR-001). */
@Entity
@Table(name = "appointment_type")
public class AppointmentType {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "doctor_profile_id", nullable = false)
    private DoctorProfile doctorProfile;

    @Column(nullable = false)
    private String name;

    @Column(name = "fee_override", precision = 10, scale = 2)
    private BigDecimal feeOverride;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AppointmentType() {
        // JPA
    }

    public AppointmentType(DoctorProfile doctorProfile, String name, BigDecimal feeOverride) {
        this.doctorProfile = doctorProfile;
        this.name = name;
        this.feeOverride = feeOverride;
    }

    public UUID getId() {
        return id;
    }

    public DoctorProfile getDoctorProfile() {
        return doctorProfile;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getFeeOverride() {
        return feeOverride;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
