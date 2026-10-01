package com.cms.booking.domain;

import com.cms.identity.clinic.Clinic;
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
 * 068-per-clinic-fees FR-002: one Appointment Type's price at one Clinic - wins over that clinic's
 * default fee for the doctor. At most one per (clinic, appointment type), set only by that
 * clinic's ClinicAdmin (FR-005).
 */
@Entity
@Table(
        name = "clinic_appointment_type_price",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_clinic_appointment_type_price",
                        columnNames = {"clinic_id", "appointment_type_id"}))
public class ClinicAppointmentTypePrice {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "clinic_id", nullable = false)
    private Clinic clinic;

    @ManyToOne(optional = false)
    @JoinColumn(name = "appointment_type_id", nullable = false)
    private AppointmentType appointmentType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by_account_id")
    private UUID updatedByAccountId;

    protected ClinicAppointmentTypePrice() {
        // JPA
    }

    public ClinicAppointmentTypePrice(
            Clinic clinic, AppointmentType appointmentType, BigDecimal amount, UUID updatedByAccountId) {
        this.clinic = clinic;
        this.appointmentType = appointmentType;
        this.amount = amount;
        this.updatedByAccountId = updatedByAccountId;
    }

    public UUID getId() {
        return id;
    }

    public Clinic getClinic() {
        return clinic;
    }

    public AppointmentType getAppointmentType() {
        return appointmentType;
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
