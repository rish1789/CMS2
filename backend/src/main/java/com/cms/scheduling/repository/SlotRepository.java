package com.cms.scheduling.repository;

import com.cms.scheduling.domain.Slot;


import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SlotRepository extends JpaRepository<Slot, UUID> {

    /**
     * 042-day-sheet-hardening FR-011: booked/total Slot counts per Session, bulk-loaded for
     * every session on the current list page in one query (no N+1) - the day sheet's fullness
     * indicator. Deliberately computed from Slot.status alone, not Booking - a BOOKED/NO_SHOW/
     * COMPLETED status is only ever reachable via an active Booking (SlotStatus's own
     * documented transitions), and OPEN is the only status without one (including after a
     * cancellation reverts a Slot back to OPEN) - so "status <> OPEN" is exactly "has an active
     * booking" with no join needed. This also keeps the query inside com.cms.scheduling, which
     * must never depend on com.cms.booking (022's own established one-way module rule).
     */
    @Query("SELECT s.session.id AS sessionId, COUNT(s) AS totalSlots, "
            + "SUM(CASE WHEN s.status <> com.cms.scheduling.domain.SlotStatus.OPEN THEN 1L ELSE 0L END) AS bookedSlots "
            + "FROM Slot s WHERE s.session.id IN :sessionIds "
            // 063-front-desk-walk-in: a Fixed-Time session's capacity is its timed slots only -
            // walk-in line places are counted separately (countWalkInLineBySessionIdIn).
            + "AND (s.startTime IS NOT NULL OR s.session.mode = com.cms.scheduling.domain.ScheduleMode.QUEUE) "
            + "GROUP BY s.session.id")
    List<SlotCountBySession> countBySessionIdIn(@Param("sessionIds") Collection<UUID> sessionIds);

    /**
     * 063-front-desk-walk-in (research.md Decision 6): per session, how many patients are waiting
     * in its untimed line (untimed BOOKED slots) and whether anyone is in with the doctor right now
     * (any APPEARED slot, the same status the Day Sheet shows). Computed from Slot status alone,
     * keeping this query in com.cms.scheduling (022's one-way module rule), like countBySessionIdIn
     * above.
     *
     * <p>064-queue-send-in-complete (research.md Decision 4): no longer Fixed-Time-only. In a
     * Fixed-Time session the untimed line is its walk-ins; in a Queue session it is every waiting
     * token (booked ahead or walked in), now that tokens are minted BOOKED - the same expressions
     * hold for both modes.
     */
    @Query("SELECT s.session.id AS sessionId, "
            + "SUM(CASE WHEN s.startTime IS NULL AND s.status = com.cms.scheduling.domain.SlotStatus.BOOKED THEN 1L ELSE 0L END) AS walkInsWaiting, "
            + "SUM(CASE WHEN s.status = com.cms.scheduling.domain.SlotStatus.APPEARED THEN 1L ELSE 0L END) AS appearedCount "
            + "FROM Slot s WHERE s.session.id IN :sessionIds "
            + "GROUP BY s.session.id")
    List<WalkInLineBySession> countWalkInLineBySessionIdIn(@Param("sessionIds") Collection<UUID> sessionIds);

    /**
     * 051-staff-dashboard-enhancement T007 (research.md Decision 2): today's Slot-status
     * breakdown for a clinic's dashboard "Today's stats" tile - the same GROUP BY-on-Slot
     * shape as {@link #countBySessionIdIn} above, scoped by clinic+date instead of a session-id
     * set. A status with zero matching Slots today produces no row at all (standard GROUP BY
     * semantics) - the caller folds missing groups to 0, same handling
     * {@code SessionSummaryResponse.from}'s null-coalescing already establishes for the sibling
     * endpoint.
     *
     * <p>doctor-console-cross-doctor-leak fix: {@code doctorProfileId} follows
     * {@link #findOpenFixedTimeSlots}'s {@code (:doctorProfileId IS NULL OR ...)} pattern - a
     * caller whose only active role at this clinic is Doctor passes their own id here so this
     * tile reflects only their own completed/no-show counts, not the whole clinic's.
     */
    @Query("SELECT s.status AS status, COUNT(s) AS count FROM Slot s "
            + "WHERE s.session.clinic.id = :clinicId AND s.session.sessionDate = :date "
            + "AND (:doctorProfileId IS NULL OR s.session.doctorProfile.id = :doctorProfileId) GROUP BY s.status")
    List<SlotStatusCount> countStatusByClinicAndDate(
            @Param("clinicId") UUID clinicId,
            @Param("date") LocalDate date,
            @Param("doctorProfileId") UUID doctorProfileId);

    interface SlotStatusCount {
        com.cms.scheduling.domain.SlotStatus getStatus();

        long getCount();
    }

    interface SlotCountBySession {
        UUID getSessionId();

        long getTotalSlots();

        long getBookedSlots();
    }

    /** 063-front-desk-walk-in: projection for {@link #countWalkInLineBySessionIdIn}. */
    interface WalkInLineBySession {
        UUID getSessionId();

        long getWalkInsWaiting();

        long getAppearedCount();
    }

    /**
     * day-sheet-slot-ordering-fix: explicit ORDER BY - without one, any UPDATE to a Slot row
     * (a booking, a status change from the no-show sweep, etc.) can move its position in an
     * otherwise-unordered scan, since SQL row order is undefined without one. {@code
     * tokenNumber} is the secondary key for Queue-mode Sessions, where every row's {@code
     * startTime} is null (Slot's Queue/Token constructor) and the primary key alone would
     * leave those unordered.
     */
    @Query("SELECT s FROM Slot s WHERE s.session.id = :sessionId ORDER BY s.startTime ASC, s.tokenNumber ASC")
    List<Slot> findBySession_Id(@Param("sessionId") UUID sessionId);

    /** 019: the current highest token number issued for a Session, or empty if none. */
    @Query("SELECT MAX(s.tokenNumber) FROM Slot s WHERE s.session.id = :sessionId")
    Optional<Integer> findMaxTokenNumberBySession_Id(@Param("sessionId") UUID sessionId);

    /**
     * 021-patient-self-service-booking: currently-OPEN Fixed-Time Slots at a clinic,
     * optionally narrowed to one doctor - excludes Queue-mode Sessions' Slots entirely
     * (data-model.md), since those are a separate feature's (018) concern.
     *
     * <p>pagination-unification-2026-09-10: paginated - a clinic-wide open-slot listing with no
     * doctor filter can span every Fixed-Time doctor's entire remaining inventory.
     *
     * <p>patient-slot-booking-date-logic: {@code s.session.sessionDate >= :from} is
     * unconditional - a past-dated OPEN Slot (nobody booked it before its day passed) must
     * never be offered to a patient. Every present-or-future date, unfiltered - see {@link
     * #findOpenFixedTimeSlotsOnDate} for the date-strip picker's exact-day variant.
     *
     * <p>065-phase1-stabilization (research.md R4): the listing applies the same rule as
     * {@code SessionAvailabilityService} - {@code :nowTime} (the server's current time, from that
     * service's clock) drops today's elapsed slots, and the {@code NOT EXISTS} drops slots of a
     * whole-cancelled session or inside a cancelled range.
     *
     * <p>Deliberately a separate query method, not one method taking a nullable {@code date}
     * guarded by {@code date IS NULL OR sessionDate = date} - two attempts at that (a bare
     * comparison, then wrapping both sides in {@code CAST(:date AS date)}) each worked in
     * isolated manual testing but then failed live once the connection pool's prepared
     * statements crossed pgJDBC's server-side-prepare promotion threshold (default: the 6th
     * execution of the same SQL text) - {@code ERROR: cannot cast type bytea to date}/
     * {@code could not determine data type of parameter}, on both the content query and Spring
     * Data's auto-derived count query. Postgres resolves a parameter's type once, at PREPARE
     * time, from the SQL text alone; a parameter whose only appearance is inside an
     * {@code IS NULL} check (even cast) gives it nothing reliable to resolve against once that
     * resolution is no longer redone per-execution. Splitting into two unambiguous queries -
     * this one never mentions {@code date} at all, {@link #findOpenFixedTimeSlotsOnDate} always
     * binds a real, non-null one - removes the ambiguity structurally instead of fighting
     * Postgres's inference. {@code :doctorProfileId}'s identical-shaped {@code IS NULL OR}
     * guard is fine left as is: a UUID parameter doesn't hit this failure mode.
     */
    @Query(
            value = "SELECT s FROM Slot s "
                    + "WHERE s.session.clinic.id = :clinicId "
                    + "AND s.session.mode = com.cms.scheduling.domain.ScheduleMode.FIXED_TIME "
                    + "AND s.status = com.cms.scheduling.domain.SlotStatus.OPEN "
                    // 063-front-desk-walk-in: an untimed slot (walk-in line) is never an offerable time.
                    + "AND s.startTime IS NOT NULL "
                    + "AND s.session.sessionDate >= :from "
                    // 065-phase1-stabilization: never today's elapsed slots (a slot starting exactly now is still offered).
                    + "AND (s.session.sessionDate > :from OR s.startTime >= :nowTime) "
                    // 065: nor a slot in a whole-cancelled session, or inside a cancelled [from, to) range.
                    + "AND NOT EXISTS (SELECT c FROM SessionCancellation c WHERE c.session.id = s.session.id "
                    + "AND (c.fromTime IS NULL OR (c.fromTime <= s.startTime AND (c.toTime IS NULL OR s.startTime < c.toTime)))) "
                    + "AND (:doctorProfileId IS NULL OR s.session.doctorProfile.id = :doctorProfileId) "
                    + "ORDER BY s.session.sessionDate ASC, s.startTime ASC",
            countQuery = "SELECT COUNT(s) FROM Slot s "
                    + "WHERE s.session.clinic.id = :clinicId "
                    + "AND s.session.mode = com.cms.scheduling.domain.ScheduleMode.FIXED_TIME "
                    + "AND s.status = com.cms.scheduling.domain.SlotStatus.OPEN "
                    // 063-front-desk-walk-in: an untimed slot (walk-in line) is never an offerable time.
                    + "AND s.startTime IS NOT NULL "
                    + "AND s.session.sessionDate >= :from "
                    // 065-phase1-stabilization: never today's elapsed slots (a slot starting exactly now is still offered).
                    + "AND (s.session.sessionDate > :from OR s.startTime >= :nowTime) "
                    // 065: nor a slot in a whole-cancelled session, or inside a cancelled [from, to) range.
                    + "AND NOT EXISTS (SELECT c FROM SessionCancellation c WHERE c.session.id = s.session.id "
                    + "AND (c.fromTime IS NULL OR (c.fromTime <= s.startTime AND (c.toTime IS NULL OR s.startTime < c.toTime)))) "
                    + "AND (:doctorProfileId IS NULL OR s.session.doctorProfile.id = :doctorProfileId)")
    Page<Slot> findOpenFixedTimeSlots(
            @Param("clinicId") UUID clinicId,
            @Param("doctorProfileId") UUID doctorProfileId,
            @Param("from") LocalDate from,
            @Param("nowTime") LocalTime nowTime,
            Pageable pageable);

    /**
     * patient-slot-booking-date-logic: the date-strip picker's exact-day variant of {@link
     * #findOpenFixedTimeSlots} - see that method's javadoc for why this is a separate query
     * rather than one method with a nullable {@code date}. {@code sessionDate >= :from} is kept
     * here too (redundant whenever {@code date} itself is already present-or-future, which every
     * real caller ensures) purely as defense in depth: even a caller that passes a past
     * {@code date} directly gets zero rows, not a bypass of the "never past" floor.
     */
    @Query(
            value = "SELECT s FROM Slot s "
                    + "WHERE s.session.clinic.id = :clinicId "
                    + "AND s.session.mode = com.cms.scheduling.domain.ScheduleMode.FIXED_TIME "
                    + "AND s.status = com.cms.scheduling.domain.SlotStatus.OPEN "
                    // 063-front-desk-walk-in: an untimed slot (walk-in line) is never an offerable time.
                    + "AND s.startTime IS NOT NULL "
                    + "AND s.session.sessionDate = :date "
                    + "AND s.session.sessionDate >= :from "
                    // 065-phase1-stabilization: never today's elapsed slots (a slot starting exactly now is still offered).
                    + "AND (s.session.sessionDate > :from OR s.startTime >= :nowTime) "
                    // 065: nor a slot in a whole-cancelled session, or inside a cancelled [from, to) range.
                    + "AND NOT EXISTS (SELECT c FROM SessionCancellation c WHERE c.session.id = s.session.id "
                    + "AND (c.fromTime IS NULL OR (c.fromTime <= s.startTime AND (c.toTime IS NULL OR s.startTime < c.toTime)))) "
                    + "AND (:doctorProfileId IS NULL OR s.session.doctorProfile.id = :doctorProfileId) "
                    + "ORDER BY s.startTime ASC",
            countQuery = "SELECT COUNT(s) FROM Slot s "
                    + "WHERE s.session.clinic.id = :clinicId "
                    + "AND s.session.mode = com.cms.scheduling.domain.ScheduleMode.FIXED_TIME "
                    + "AND s.status = com.cms.scheduling.domain.SlotStatus.OPEN "
                    // 063-front-desk-walk-in: an untimed slot (walk-in line) is never an offerable time.
                    + "AND s.startTime IS NOT NULL "
                    + "AND s.session.sessionDate = :date "
                    + "AND s.session.sessionDate >= :from "
                    // 065-phase1-stabilization: never today's elapsed slots (a slot starting exactly now is still offered).
                    + "AND (s.session.sessionDate > :from OR s.startTime >= :nowTime) "
                    // 065: nor a slot in a whole-cancelled session, or inside a cancelled [from, to) range.
                    + "AND NOT EXISTS (SELECT c FROM SessionCancellation c WHERE c.session.id = s.session.id "
                    + "AND (c.fromTime IS NULL OR (c.fromTime <= s.startTime AND (c.toTime IS NULL OR s.startTime < c.toTime)))) "
                    + "AND (:doctorProfileId IS NULL OR s.session.doctorProfile.id = :doctorProfileId)")
    Page<Slot> findOpenFixedTimeSlotsOnDate(
            @Param("clinicId") UUID clinicId,
            @Param("doctorProfileId") UUID doctorProfileId,
            @Param("date") LocalDate date,
            @Param("from") LocalDate from,
            @Param("nowTime") LocalTime nowTime,
            Pageable pageable);

    /**
     * 023-no-show-detection: candidate Slots for the automatic sweep - still BOOKED, not on
     * hold, Fixed-Time only (Clarifications). The grace-period elapsed check itself happens
     * in Java, not here (research.md - combining Session.sessionDate + Slot.startTime into a
     * single comparable instant isn't a clean single SQL predicate across this join).
     */
    @Query("SELECT s FROM Slot s "
            + "WHERE s.status = com.cms.scheduling.domain.SlotStatus.BOOKED "
            + "AND s.onHold = false "
            // 063-front-desk-walk-in: a waiting walk-in has no scheduled time to be late for.
            + "AND s.startTime IS NOT NULL "
            + "AND s.session.mode = com.cms.scheduling.domain.ScheduleMode.FIXED_TIME")
    List<Slot> findBookedFixedTimeCandidatesForNoShow();

    /**
     * 057-day-sheet-status-overhaul: candidate Slots for the automatic completion sweep -
     * currently APPEARED, Fixed-Time only, mirroring findBookedFixedTimeCandidatesForNoShow's
     * shape exactly. The scheduled-end-time-elapsed check itself happens in Java, not here,
     * for the same reason the No-Show sweep's grace-period check does (research.md).
     */
    @Query("SELECT s FROM Slot s "
            + "WHERE s.status = com.cms.scheduling.domain.SlotStatus.APPEARED "
            // 063-front-desk-walk-in: a walk-in in with the doctor has no scheduled end - it stays
            // In with doctor until someone completes the visit.
            + "AND s.startTime IS NOT NULL "
            + "AND s.session.mode = com.cms.scheduling.domain.ScheduleMode.FIXED_TIME")
    List<Slot> findAppearedFixedTimeCandidatesForAutoCompletion();
}
