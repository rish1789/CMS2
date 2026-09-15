package com.cms.booking;

import com.cms.patient.record.Patient;
import com.cms.scheduling.Slot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * 020: one appointment - first defined by this feature. At most one *active* Booking per Slot at
 * any moment (the {@code uq_booking_slot_active} partial index, 028 - not expressible via JPA's
 * {@code @UniqueConstraint}, mirrors {@code Patient}'s (019) own partial-constraint precedent of
 * leaving it to Flyway alone) - a cancelled Booking (028) is retained, not deleted, so a Slot can
 * carry multiple historical Bookings over repeated book/cancel/rebook cycles.
 */
@Entity
@Table(name = "booking")
public class Booking {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @OneToOne(optional = false)
    @JoinColumn(name = "slot_id", nullable = false)
    private Slot slot;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(optional = false)
    @JoinColumn(name = "appointment_type_id", nullable = false)
    private AppointmentType appointmentType;

    @Column(name = "locked_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal lockedFee;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    /** Set for a staff-initiated booking (016/018/020); null for a patient self-service booking. */
    @Column(name = "booked_by_account_id")
    private UUID bookedByAccountId;

    /**
     * _diagnostics CRITICAL - [BOOKING] - [FK_INTEGRITY_GAP]: set for a patient self-service
     * booking (017/018), which has no {@code account.id} to source at all - {@code account} and
     * {@code patient_account} are deliberately disjoint identity systems (see {@code
     * V2__create_patient_account.sql}). Null for a staff-initiated booking. Exactly one of this
     * field and {@link #bookedByAccountId} is non-null, enforced by {@code
     * ck_booking_booked_by_exactly_one} (V24).
     */
    @Column(name = "booked_by_patient_account_id")
    private UUID bookedByPatientAccountId;

    /** 025-walk-in-priority-insertion: the required written justification for a priority-(3) walk-in insertion; null for every other Booking. */
    @Column(name = "override_reason")
    private String overrideReason;

    /** 028-individual-booking-cancellation: independent of {@code paymentStatus} - defaults ACTIVE, flipped to CANCELLED only via {@code BookingRepository.cancelIfActive}'s data-layer-guarded update. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    /** patient-cancellation-reason: null for an ACTIVE Booking, or one cancelled by staff (never required of them). Set only via the reason-carrying {@code cancelIfActive} overload. */
    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_reason")
    private BookingCancellationReason cancellationReason;

    /** patient-cancellation-reason: optional free-text elaboration alongside {@link #cancellationReason} - never required on its own. */
    @Column(name = "cancellation_reason_detail")
    private String cancellationReasonDetail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Booking() {
        // JPA
    }

    public Booking(
            Slot slot, Patient patient, AppointmentType appointmentType, BigDecimal lockedFee, UUID bookedByAccountId) {
        this(slot, patient, appointmentType, lockedFee, bookedByAccountId, null);
    }

    /** 025: the priority-(3) walk-in insertion path - {@code overrideReason} is non-null here, null via the 5-arg constructor every other caller (016/017/018/022) still uses. */
    public Booking(
            Slot slot,
            Patient patient,
            AppointmentType appointmentType,
            BigDecimal lockedFee,
            UUID bookedByAccountId,
            String overrideReason) {
        this.slot = slot;
        this.patient = patient;
        this.appointmentType = appointmentType;
        this.lockedFee = lockedFee;
        this.paymentStatus = PaymentStatus.PENDING;
        this.bookedByAccountId = bookedByAccountId;
        this.overrideReason = overrideReason;
        this.status = BookingStatus.ACTIVE;
    }

    /**
     * _diagnostics CRITICAL - [BOOKING] - [FK_INTEGRITY_GAP]: the patient self-service booking
     * path (017/018) - {@code bookedByPatientAccountId} is a {@code patient_account.id}, never an
     * {@code account.id}. Use this instead of the staff-oriented constructors above whenever the
     * caller is an authenticated patient, not staff.
     */
    public static Booking bookedByPatient(
            Slot slot, Patient patient, AppointmentType appointmentType, BigDecimal lockedFee, UUID patientAccountId) {
        Booking booking = new Booking();
        booking.slot = slot;
        booking.patient = patient;
        booking.appointmentType = appointmentType;
        booking.lockedFee = lockedFee;
        booking.paymentStatus = PaymentStatus.PENDING;
        booking.bookedByPatientAccountId = patientAccountId;
        booking.status = BookingStatus.ACTIVE;
        return booking;
    }

    public UUID getId() {
        return id;
    }

    public Slot getSlot() {
        return slot;
    }

    public Patient getPatient() {
        return patient;
    }

    public AppointmentType getAppointmentType() {
        return appointmentType;
    }

    public BigDecimal getLockedFee() {
        return lockedFee;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public UUID getBookedByAccountId() {
        return bookedByAccountId;
    }

    public UUID getBookedByPatientAccountId() {
        return bookedByPatientAccountId;
    }

    public String getOverrideReason() {
        return overrideReason;
    }

    public BookingStatus getStatus() {
        return status;
    }

    /**
     * 028-individual-booking-cancellation: called only after {@code BookingRepository
     * .cancelIfActive}'s data-layer-guarded update has already confirmed the transition - this
     * setter exists purely to keep this in-memory entity consistent with that DB-level change
     * (the {@code @Modifying} query bypasses the persistence context, so this object's field
     * would otherwise read stale for the rest of the transaction, e.g. when building the
     * response), not as a second place the transition itself is decided.
     */
    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public BookingCancellationReason getCancellationReason() {
        return cancellationReason;
    }

    public String getCancellationReasonDetail() {
        return cancellationReasonDetail;
    }

    /**
     * patient-cancellation-reason: same "keep the in-memory entity consistent with a
     * {@code cancelIfActive} update that bypassed the persistence context" contract as {@link
     * #setStatus} - called only after that guarded update has already persisted these values.
     * A no-op (both null) whenever the caller didn't collect a reason, e.g. a staff-initiated
     * cancellation.
     */
    public void recordCancellationReason(BookingCancellationReason cancellationReason, String cancellationReasonDetail) {
        this.cancellationReason = cancellationReason;
        this.cancellationReasonDetail = cancellationReasonDetail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
