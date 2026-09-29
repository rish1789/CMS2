package com.cms.booking.repository;

import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingCancellationReason;
import com.cms.booking.domain.BookingStatus;


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
    @Query("UPDATE Booking b SET b.status = com.cms.booking.domain.BookingStatus.CANCELLED, "
            + "b.cancelledAt = CURRENT_TIMESTAMP "
            + "WHERE b.id = :id AND b.status <> com.cms.booking.domain.BookingStatus.CANCELLED")
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
    @Query("UPDATE Booking b SET b.status = com.cms.booking.domain.BookingStatus.CANCELLED, "
            + "b.cancellationReason = :reason, b.cancellationReasonDetail = :reasonDetail, "
            + "b.cancelledAt = CURRENT_TIMESTAMP "
            + "WHERE b.id = :id AND b.status <> com.cms.booking.domain.BookingStatus.CANCELLED")
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
            + "AND b.slot.session.mode = com.cms.scheduling.domain.ScheduleMode.FIXED_TIME "
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
            + "WHERE b.status = com.cms.booking.domain.BookingStatus.ACTIVE "
            // real-bug-fix 2026-09-24 + 063-front-desk-walk-in (T039): a timed BOOKED slot is pending.
            // An untimed slot - a queue token (stays OPEN until seen) or a Fixed-Time walk-in line
            // place (BOOKED until sent in) - is pending only for today or later: nothing ever
            // resolves a stale past one, and it must neither block anonymization forever nor be
            // swept up as a "future" booking.
            + "AND ((b.slot.status = com.cms.scheduling.domain.SlotStatus.BOOKED AND b.slot.startTime IS NOT NULL) "
            + "OR (b.slot.status IN (com.cms.scheduling.domain.SlotStatus.BOOKED, com.cms.scheduling.domain.SlotStatus.OPEN) "
            + "AND b.slot.startTime IS NULL AND b.slot.session.sessionDate >= CURRENT_DATE)) "
            + "AND b.slot.session.clinic.id = :clinicId")
    List<Booking> findActiveFutureBookingsByClinic(@Param("clinicId") UUID clinicId);

    /**
     * 062-rejected-clinic-gating (research.md Decision 4): a rejected clinic's appointments that
     * haven't happened yet - still ACTIVE, slot still BOOKED or OPEN (never appeared/completed/no-show; a queue token stays OPEN until seen),
     * session today or later. Unlike {@link #findActiveFutureBookingsByClinic}, past-dated
     * sessions are excluded outright (FR-009: past bookings untouched).
     */
    @Query("SELECT b FROM Booking b "
            + "WHERE b.status = com.cms.booking.domain.BookingStatus.ACTIVE "
            // real-bug-fix 2026-09-24: OPEN too - a real queue booking's token Slot stays OPEN
            // until the patient is seen (only fixed-time booking flips a Slot to BOOKED).
            + "AND b.slot.status IN (com.cms.scheduling.domain.SlotStatus.BOOKED, com.cms.scheduling.domain.SlotStatus.OPEN) "
            + "AND b.slot.session.clinic.id = :clinicId "
            + "AND b.slot.session.sessionDate >= :today")
    List<Booking> findActiveUpcomingBookingsByClinic(@Param("clinicId") UUID clinicId, @Param("today") LocalDate today);

    /** 033 research.md R5: the same, scoped by doctor instead - spans every clinic that doctor is staffed at. */
    @Query("SELECT b FROM Booking b "
            + "WHERE b.status = com.cms.booking.domain.BookingStatus.ACTIVE "
            // real-bug-fix 2026-09-24 + 063-front-desk-walk-in (T039): a timed BOOKED slot is pending.
            // An untimed slot - a queue token (stays OPEN until seen) or a Fixed-Time walk-in line
            // place (BOOKED until sent in) - is pending only for today or later: nothing ever
            // resolves a stale past one, and it must neither block anonymization forever nor be
            // swept up as a "future" booking.
            + "AND ((b.slot.status = com.cms.scheduling.domain.SlotStatus.BOOKED AND b.slot.startTime IS NOT NULL) "
            + "OR (b.slot.status IN (com.cms.scheduling.domain.SlotStatus.BOOKED, com.cms.scheduling.domain.SlotStatus.OPEN) "
            + "AND b.slot.startTime IS NULL AND b.slot.session.sessionDate >= CURRENT_DATE)) "
            + "AND b.slot.session.doctorProfile.id = :doctorProfileId")
    List<Booking> findActiveFutureBookingsByDoctor(@Param("doctorProfileId") UUID doctorProfileId);

    /**
     * 041-staff-console-pickers research.md R3: every ACTIVE Booking for a whole Session in one
     * query - the day sheet's batch lookup, avoiding an N+1 per-slot query.
     */
    @Query("SELECT b FROM Booking b WHERE b.slot.session.id = :sessionId AND b.status = :status")
    List<Booking> findBySlot_Session_IdAndStatus(
            @Param("sessionId") UUID sessionId, @Param("status") BookingStatus status);

    /** 063-front-desk-walk-in (FR-017): is this patient already waiting in, or booked into, this session? */
    boolean existsBySlot_Session_IdAndPatient_IdAndStatus(UUID sessionId, UUID patientId, BookingStatus status);

    /** 037 research.md R2: the same "not yet occurred" definition, scoped by patient - the anonymization block precondition. */
    @Query("SELECT COUNT(b) > 0 FROM Booking b "
            + "WHERE b.status = com.cms.booking.domain.BookingStatus.ACTIVE "
            // real-bug-fix 2026-09-24 + 063-front-desk-walk-in (T039): a timed BOOKED slot is pending.
            // An untimed slot - a queue token (stays OPEN until seen) or a Fixed-Time walk-in line
            // place (BOOKED until sent in) - is pending only for today or later: nothing ever
            // resolves a stale past one, and it must neither block anonymization forever nor be
            // swept up as a "future" booking.
            + "AND ((b.slot.status = com.cms.scheduling.domain.SlotStatus.BOOKED AND b.slot.startTime IS NOT NULL) "
            + "OR (b.slot.status IN (com.cms.scheduling.domain.SlotStatus.BOOKED, com.cms.scheduling.domain.SlotStatus.OPEN) "
            + "AND b.slot.startTime IS NULL AND b.slot.session.sessionDate >= CURRENT_DATE)) "
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

    /**
     * 059-patient-clinical-record-access (research.md Decision 1): the single-booking ownership
     * check every new patient-facing clinical-record lookup uses - the exact same
     * {@code patient.patientAccount.id} predicate as
     * {@link #findByPatient_PatientAccount_IdOrderByCreatedAtDesc} above, scoped to one booking
     * id instead of a page. A booking whose Patient has no linked PatientAccount (walk-in/
     * staff-created) never matches any caller.
     */
    Optional<Booking> findByIdAndPatient_PatientAccount_Id(UUID id, UUID patientAccountId);

    /**
     * 052-patient-clinical-hub T003 (research.md Decision 2): a clinic-scoped patient's full
     * booking history, for the staff-side Patient Hub. Mirrors
     * {@link #findByPatient_PatientAccount_IdOrderByCreatedAtDesc} above, but scoped by
     * {@code patient.id} (a Patient row belongs to exactly one clinic) rather than
     * {@code patient.patientAccount.id} (which spans every clinic) - the caller (
     * {@code PatientBookingHistoryController}) confirms {@code patientId} belongs to the
     * caller's clinic before this query ever runs, so the result cannot include another
     * clinic's data by construction.
     *
     * <p>doctor-console-cross-doctor-leak fix: {@code doctorProfileId} follows
     * {@code SlotRepository.findOpenFixedTimeSlots}'s {@code (:doctorProfileId IS NULL OR ...)}
     * pattern - a caller whose only active role at this clinic is Doctor passes their own id
     * here so a patient's Bookings tab shows only that patient's encounters with them, not the
     * patient's history with every other doctor at the clinic.
     */
    @Query("SELECT b FROM Booking b "
            + "WHERE b.patient.id = :patientId "
            + "AND (:doctorProfileId IS NULL OR b.slot.session.doctorProfile.id = :doctorProfileId) "
            + "ORDER BY b.slot.session.sessionDate DESC")
    Page<Booking> findByPatient_IdOrderBySlot_Session_SessionDateDesc(
            @Param("patientId") UUID patientId, @Param("doctorProfileId") UUID doctorProfileId, Pageable pageable);

    /**
     * real-bug-fix 2026-09-17: the session-deletion guard - any Booking at all (any status,
     * including a retained-not-deleted cancelled one) ever created against this Session counts as
     * real activity and blocks the delete, mirroring ClinicVerificationService.deleteGuarded's own
     * "block, don't cascade through booking history" precedent.
     */
    long countBySlot_Session_Id(UUID sessionId);

    /**
     * real-bug-fix 2026-09-17: every currently-ACTIVE Booking at a clinic for one calendar
     * date, across every doctor and both Session modes - the Find a Patient page's "today's
     * patients" table. ACTIVE-only mirrors {@link #findBySlot_Session_IdAndStatus}'s own Day
     * Sheet precedent (041) - a cancelled Booking's Slot reverts to OPEN and simply isn't in this
     * list, the same "cancelled = invisible in every operational view, retained only for audit"
     * rule this codebase already applies everywhere else. Ordered by the owning Session's own
     * start time, then the Slot's - a Queue-mode Slot's null startTime sorts after every
     * Fixed-Time one on the same date, which is an acceptable, stable tie-break (no spec
     * requirement orders Queue-mode entries by anything more specific than "this session").
     *
     * <p>doctor-console-cross-doctor-leak fix: {@code doctorProfileId} follows
     * {@code SlotRepository.findOpenFixedTimeSlots}'s {@code (:doctorProfileId IS NULL OR ...)}
     * pattern - a caller whose only active role at this clinic is Doctor passes their own id
     * here so this roster shows only their own patients, not every doctor's at the clinic.
     */
    @Query("SELECT b FROM Booking b "
            + "WHERE b.status = com.cms.booking.domain.BookingStatus.ACTIVE "
            + "AND b.slot.session.clinic.id = :clinicId "
            + "AND b.slot.session.sessionDate = :date "
            + "AND (:doctorProfileId IS NULL OR b.slot.session.doctorProfile.id = :doctorProfileId) "
            + "ORDER BY b.slot.session.startTime ASC, b.slot.startTime ASC")
    List<Booking> findActiveByClinicAndSessionDate(
            @Param("clinicId") UUID clinicId,
            @Param("date") LocalDate date,
            @Param("doctorProfileId") UUID doctorProfileId);

    /**
     * 060-booking-abuse-prevention FR-001/FR-003: the global active-appointment count for
     * BookingProtectionService.checkBookingLimit - every clinic, ACTIVE status only (a cancelled
     * booking never counts, regardless of when it was cancelled).
     */
    long countByPatient_PatientAccount_IdAndStatus(UUID patientAccountId, BookingStatus status);

    /** FR-004: the same count, scoped to one clinic, for the optional per-clinic supplementary limit. */
    long countByPatient_PatientAccount_IdAndStatusAndPatient_Clinic_Id(
            UUID patientAccountId, BookingStatus status, UUID clinicId);

    /**
     * 060-booking-abuse-prevention FR-017: every (patient, clinic) pair with a cancellation since
     * a given time, grouped - FlagDetectionService filters this for count >= the signal's own
     * threshold. {@code cancelledAt} is null for a pre-existing cancellation predating that
     * column (V38), so those never contribute here - a one-time historical gap, not an ongoing one.
     */
    @Query("SELECT b.patient.patientAccount.id AS patientAccountId, b.patient.clinic.id AS clinicId, COUNT(b) AS count "
            + "FROM Booking b WHERE b.status = com.cms.booking.domain.BookingStatus.CANCELLED "
            + "AND b.patient.patientAccount.id IS NOT NULL AND b.cancelledAt >= :since "
            + "GROUP BY b.patient.patientAccount.id, b.patient.clinic.id")
    List<PatientClinicCount> countCancellationsByPatientAndClinicSince(@Param("since") Instant since);

    /**
     * 060-booking-abuse-prevention FR-018: every (patient, clinic) pair with a no-show since a
     * given date, grouped - reads {@code Slot.status} through the existing {@code booking ->
     * scheduling} one-way dependency (this query lives in {@code booking}, never in {@code
     * scheduling} itself, which must never depend back on {@code booking} - 022's established
     * rule). {@code sessionDate} (the missed appointment's own date), not a detection timestamp
     * that doesn't exist, is the correct anchor for "when this no-show happened."
     */
    @Query("SELECT b.patient.patientAccount.id AS patientAccountId, b.patient.clinic.id AS clinicId, COUNT(b) AS count "
            + "FROM Booking b WHERE b.slot.status = com.cms.scheduling.domain.SlotStatus.NO_SHOW "
            + "AND b.patient.patientAccount.id IS NOT NULL AND b.slot.session.sessionDate >= :since "
            + "GROUP BY b.patient.patientAccount.id, b.patient.clinic.id")
    List<PatientClinicCount> countNoShowsByPatientAndClinicSince(@Param("since") LocalDate since);

    /**
     * 060-booking-abuse-prevention FR-019: every currently-ACTIVE booking's own schedule
     * (patient/clinic/date/start-end time), for FlagDetectionService's overlapping-appointments
     * signal to compute actual time-range overlap in Java (a self-join expressing "two of this
     * patient's own active bookings overlap" cleanly in JPQL/SQL is more complex than computing
     * it directly over a bounded per-patient result set already grouped by the query below).
     */
    @Query("SELECT b.patient.patientAccount.id AS patientAccountId, b.slot.session.clinic.id AS clinicId, "
            + "b.slot.session.sessionDate AS sessionDate, b.slot.startTime AS startTime, b.slot.endTime AS endTime "
            + "FROM Booking b WHERE b.status = com.cms.booking.domain.BookingStatus.ACTIVE "
            + "AND b.patient.patientAccount.id IS NOT NULL AND b.slot.session.mode = com.cms.scheduling.domain.ScheduleMode.FIXED_TIME "
            + "AND b.slot.session.sessionDate >= :since")
    List<ActiveBookingSchedule> findActiveFixedTimeSchedulesSince(@Param("since") LocalDate since);

    /** 060-booking-abuse-prevention FR-021: recent active bookings for one clinic-scoped flag-review evidence panel. */
    List<Booking> findTop10ByPatient_PatientAccount_IdAndPatient_Clinic_IdAndStatusOrderByCreatedAtDesc(
            UUID patientAccountId, UUID clinicId, BookingStatus status);

    /** FR-021/FR-017: recent cancellations for the same evidence panel. */
    @Query("SELECT b FROM Booking b WHERE b.patient.patientAccount.id = :patientAccountId "
            + "AND b.patient.clinic.id = :clinicId AND b.status = com.cms.booking.domain.BookingStatus.CANCELLED "
            + "ORDER BY b.cancelledAt DESC")
    List<Booking> findRecentCancellations(
            @Param("patientAccountId") UUID patientAccountId, @Param("clinicId") UUID clinicId, Pageable pageable);

    /** FR-021/FR-018: recent no-shows for the same evidence panel. */
    @Query("SELECT b FROM Booking b WHERE b.patient.patientAccount.id = :patientAccountId "
            + "AND b.patient.clinic.id = :clinicId AND b.slot.status = com.cms.scheduling.domain.SlotStatus.NO_SHOW "
            + "ORDER BY b.slot.session.sessionDate DESC")
    List<Booking> findRecentNoShows(
            @Param("patientAccountId") UUID patientAccountId, @Param("clinicId") UUID clinicId, Pageable pageable);

    interface PatientClinicCount {
        UUID getPatientAccountId();

        UUID getClinicId();

        long getCount();
    }

    interface ActiveBookingSchedule {
        UUID getPatientAccountId();

        UUID getClinicId();

        LocalDate getSessionDate();

        java.time.LocalTime getStartTime();

        java.time.LocalTime getEndTime();
    }
}
