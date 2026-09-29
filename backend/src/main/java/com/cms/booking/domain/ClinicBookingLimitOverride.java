package com.cms.booking.domain;

import com.cms.identity.clinic.Clinic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * 060-booking-abuse-prevention (spec.md FR-004, US5): an optional, per-clinic supplementary cap
 * on top of the platform-wide active-appointment limit. At most one row per clinic (unique
 * index) - absence of a row means "no override, global limit only," not zero.
 */
@Entity
@Table(name = "clinic_booking_limit_override")
public class ClinicBookingLimitOverride {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "clinic_id", nullable = false)
    private Clinic clinic;

    @Column(name = "max_active_appointments", nullable = false)
    private int maxActiveAppointments;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    protected ClinicBookingLimitOverride() {
        // JPA
    }

    public ClinicBookingLimitOverride(Clinic clinic, int maxActiveAppointments, Instant updatedAt, String updatedBy) {
        this.clinic = clinic;
        this.maxActiveAppointments = maxActiveAppointments;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    public UUID getId() {
        return id;
    }

    public Clinic getClinic() {
        return clinic;
    }

    public int getMaxActiveAppointments() {
        return maxActiveAppointments;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    /** BR-005-validated by the caller before this is invoked - see ClinicBookingLimitOverrideController. */
    public void update(int maxActiveAppointments, Instant updatedAt, String updatedBy) {
        this.maxActiveAppointments = maxActiveAppointments;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }
}
