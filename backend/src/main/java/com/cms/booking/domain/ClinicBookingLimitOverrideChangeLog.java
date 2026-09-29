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
 * 060-booking-abuse-prevention (spec.md AUD-003, research.md Decision 9): append-only audit
 * history for {@link ClinicBookingLimitOverride}. Lives in {@code booking}, the same module that
 * owns the table it audits, so writing to it never crosses the booking/protection module
 * boundary. Null {@code previousMaxActiveAppointments} means this row records the override's
 * first-ever creation; null {@code newMaxActiveAppointments} means this row records its removal.
 */
@Entity
@Table(name = "clinic_booking_limit_override_change_log")
public class ClinicBookingLimitOverrideChangeLog {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "clinic_id", nullable = false)
    private Clinic clinic;

    @Column(name = "previous_max_active_appointments")
    private Integer previousMaxActiveAppointments;

    @Column(name = "new_max_active_appointments")
    private Integer newMaxActiveAppointments;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "changed_by", nullable = false)
    private String changedBy;

    protected ClinicBookingLimitOverrideChangeLog() {
        // JPA
    }

    public ClinicBookingLimitOverrideChangeLog(
            Clinic clinic,
            Integer previousMaxActiveAppointments,
            Integer newMaxActiveAppointments,
            Instant changedAt,
            String changedBy) {
        this.clinic = clinic;
        this.previousMaxActiveAppointments = previousMaxActiveAppointments;
        this.newMaxActiveAppointments = newMaxActiveAppointments;
        this.changedAt = changedAt;
        this.changedBy = changedBy;
    }

    public UUID getId() {
        return id;
    }

    public Clinic getClinic() {
        return clinic;
    }

    public Integer getPreviousMaxActiveAppointments() {
        return previousMaxActiveAppointments;
    }

    public Integer getNewMaxActiveAppointments() {
        return newMaxActiveAppointments;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public String getChangedBy() {
        return changedBy;
    }
}
