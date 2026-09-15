package com.cms.booking;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    /**
     * 028-individual-booking-cancellation: a cancelled Booking is retained, so a Slot can now
     * carry more than one historical Booking row over repeated book/cancel/rebook cycles - this
     * method throws {@code IncorrectResultSizeDataAccessException} if more than one exists.
     * Existing callers that mean "the current live Booking" MUST use
     * {@link #findBySlot_IdAndStatus} instead (fixed in {@code WalkInInsertionService}, the one
     * production call site); this method remains valid only where at most one Booking per Slot
     * is already guaranteed by context (e.g. before any cancellation has ever occurred).
     */
    Optional<Booking> findBySlot_Id(UUID slotId);

    /** 028: the current live (or specifically-cancelled) Booking for a Slot - safe even after repeated book/cancel/rebook cycles, unlike the bare {@link #findBySlot_Id}. */
    Optional<Booking> findBySlot_IdAndStatus(UUID slotId, BookingStatus status);

    /**
     * 028 FR-008/research.md R3: the actual concurrency guarantee for cancellation - a
     * data-layer-guarded conditional update, not a plain read-then-write. Returns 0 if the
     * Booking was already CANCELLED (including a lost race against a concurrent caller).
     */
    @Modifying
    @Query("UPDATE Booking b SET b.status = com.cms.booking.BookingStatus.CANCELLED "
            + "WHERE b.id = :id AND b.status <> com.cms.booking.BookingStatus.CANCELLED")
    int cancelIfActive(@Param("id") UUID id);

    /**
     * patient-cancellation-reason: the reason-carrying sibling of {@link #cancelIfActive(UUID)} -
     * added as a separate overload rather than changing that one's signature, since it has four
     * other call sites ({@code SessionCancellationService}, {@code
     * SessionPartialCancellationService}, {@code DeVerificationCascadeService}, and {@code
     * BookingCancellationService}'s own no-reason overload) that never collect a reason and
     * shouldn't need to start passing nulls for it. Used exclusively by {@code
     * BookingCancellationService}'s reason-carrying overload (the patient self-service path).
     */
    @Modifying
    @Query("UPDATE Booking b SET b.status = com.cms.booking.BookingStatus.CANCELLED, "
            + "b.cancellationReason = :reason, b.cancellationReasonDetail = :reasonDetail "
            + "WHERE b.id = :id AND b.status <> com.cms.booking.BookingStatus.CANCELLED")
    int cancelIfActive(
            @Param("id") UUID id,
            @Param("reason") BookingCancellationReason reason,
            @Param("reasonDetail") String reasonDetail);

    /**
     * 024-buffer-slot-capacity-sizing: every Fixed-Time Booking for a doctor whose Session
     * falls within the given date window (inclusive both ends) - the sample the risk-based
     * buffer calculator computes its no-show rate from (research.md).
     */
    @Query("SELECT b FROM Booking b "
            + "WHERE b.slot.session.doctorProfile.id = :doctorProfileId "
            + "AND b.slot.session.mode = com.cms.scheduling.ScheduleMode.FIXED_TIME "
            + "AND b.slot.session.sessionDate BETWEEN :windowStart AND :windowEnd")
    List<Booking> findByDoctorAndFixedTimeWindow(
            @Param("doctorProfileId") UUID doctorProfileId,
            @Param("windowStart") LocalDate windowStart,
            @Param("windowEnd") LocalDate windowEnd);

    /**
     * 033 research.md R5: every still-pending Booking at this clinic - "not yet occurred" is
     * defined structurally (the Slot hasn't reached NO_SHOW/COMPLETED/re-booked-elsewhere yet),
     * not by a calendar-date comparison, reusing 021/026's own existing Slot-lifecycle state.
     */
    @Query("SELECT b FROM Booking b "
            + "WHERE b.status = com.cms.booking.BookingStatus.ACTIVE "
            + "AND b.slot.status = com.cms.scheduling.SlotStatus.BOOKED "
            + "AND b.slot.session.clinic.id = :clinicId")
    List<Booking> findActiveFutureBookingsByClinic(@Param("clinicId") UUID clinicId);

    /** 033 research.md R5: the same, scoped by doctor instead - spans every clinic that doctor is staffed at. */
    @Query("SELECT b FROM Booking b "
            + "WHERE b.status = com.cms.booking.BookingStatus.ACTIVE "
            + "AND b.slot.status = com.cms.scheduling.SlotStatus.BOOKED "
            + "AND b.slot.session.doctorProfile.id = :doctorProfileId")
    List<Booking> findActiveFutureBookingsByDoctor(@Param("doctorProfileId") UUID doctorProfileId);

    /**
     * 041-staff-console-pickers research.md R3: every ACTIVE Booking for a whole Session in one
     * query - the day sheet's batch lookup, avoiding an N+1 per-slot query.
     */
    @Query("SELECT b FROM Booking b WHERE b.slot.session.id = :sessionId AND b.status = :status")
    List<Booking> findBySlot_Session_IdAndStatus(
            @Param("sessionId") UUID sessionId, @Param("status") BookingStatus status);

    /** 037 research.md R2: the same "not yet occurred" definition, scoped by patient - the anonymization block precondition. */
    @Query("SELECT COUNT(b) > 0 FROM Booking b "
            + "WHERE b.status = com.cms.booking.BookingStatus.ACTIVE "
            + "AND b.slot.status = com.cms.scheduling.SlotStatus.BOOKED "
            + "AND b.patient.id = :patientId")
    boolean existsActiveFutureBookingForPatient(@Param("patientId") UUID patientId);

    /**
     * 038 research.md R4: retention-purge eligibility - both the patient must already be
     * anonymized AND the booking's own {@code createdAt} must be older than the retention
     * cutoff. Deliberately no status/slot filtering (unlike the "not yet occurred" queries
     * above) - a cancelled or completed booking's clinical content is equally subject to the
     * retention window.
     */
    @Query("SELECT b FROM Booking b "
            + "WHERE b.patient.anonymizedAt IS NOT NULL "
            + "AND b.createdAt < :retentionCutoff")
    List<Booking> findRetentionEligibleBookings(@Param("retentionCutoff") Instant retentionCutoff);

    /**
     * patient-booking-flow-rebuild: "My bookings" - every Booking (any status) made by this
     * Patient Account, across every clinic they've ever booked at, newest first. Ownership is
     * via {@code patient.patientAccount}, the same traversal {@code
     * PatientBookingCancellationController} already uses to enforce access - this query just
     * lists instead of filtering a single lookup.
     */
    Page<Booking> findByPatient_PatientAccount_IdOrderByCreatedAtDesc(UUID patientAccountId, Pageable pageable);
}
