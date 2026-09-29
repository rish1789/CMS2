package com.cms.booking.domain;

import com.cms.identity.clinic.Clinic;
import com.cms.patient.account.domain.PatientAccount;
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
 * 060-booking-abuse-prevention: one row per self-service booking-creation attempt, whatever the
 * outcome (spec.md FR-008) - feeds both the rate-limit window count and the admin-flagging
 * signals that read attempt history (FR-016, FR-020). Append-only; never updated or deleted
 * outside its own retention sweep.
 */
@Entity
@Table(name = "booking_attempt_log")
public class BookingAttemptLog {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_account_id", nullable = false)
    private PatientAccount patientAccount;

    @ManyToOne(optional = false)
    @JoinColumn(name = "clinic_id", nullable = false)
    private Clinic clinic;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingAttemptOutcome outcome;

    @ManyToOne
    @JoinColumn(name = "booking_id")
    private Booking booking;

    protected BookingAttemptLog() {
        // JPA
    }

    public BookingAttemptLog(
            PatientAccount patientAccount, Clinic clinic, Instant attemptedAt, BookingAttemptOutcome outcome, Booking booking) {
        this.patientAccount = patientAccount;
        this.clinic = clinic;
        this.attemptedAt = attemptedAt;
        this.outcome = outcome;
        this.booking = booking;
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

    public Instant getAttemptedAt() {
        return attemptedAt;
    }

    public BookingAttemptOutcome getOutcome() {
        return outcome;
    }

    public Booking getBooking() {
        return booking;
    }
}
