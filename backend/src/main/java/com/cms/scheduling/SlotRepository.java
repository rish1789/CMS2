package com.cms.scheduling;

import java.time.LocalDate;
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
            + "SUM(CASE WHEN s.status <> com.cms.scheduling.SlotStatus.OPEN THEN 1L ELSE 0L END) AS bookedSlots "
            + "FROM Slot s WHERE s.session.id IN :sessionIds GROUP BY s.session.id")
    List<SlotCountBySession> countBySessionIdIn(@Param("sessionIds") Collection<UUID> sessionIds);

    interface SlotCountBySession {
        UUID getSessionId();

        long getTotalSlots();

        long getBookedSlots();
    }

    List<Slot> findBySession_Id(UUID sessionId);

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
                    + "AND s.session.mode = com.cms.scheduling.ScheduleMode.FIXED_TIME "
                    + "AND s.status = com.cms.scheduling.SlotStatus.OPEN "
                    + "AND s.session.sessionDate >= :from "
                    + "AND (:doctorProfileId IS NULL OR s.session.doctorProfile.id = :doctorProfileId) "
                    + "ORDER BY s.session.sessionDate ASC, s.startTime ASC",
            countQuery = "SELECT COUNT(s) FROM Slot s "
                    + "WHERE s.session.clinic.id = :clinicId "
                    + "AND s.session.mode = com.cms.scheduling.ScheduleMode.FIXED_TIME "
                    + "AND s.status = com.cms.scheduling.SlotStatus.OPEN "
                    + "AND s.session.sessionDate >= :from "
                    + "AND (:doctorProfileId IS NULL OR s.session.doctorProfile.id = :doctorProfileId)")
    Page<Slot> findOpenFixedTimeSlots(
            @Param("clinicId") UUID clinicId,
            @Param("doctorProfileId") UUID doctorProfileId,
            @Param("from") LocalDate from,
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
                    + "AND s.session.mode = com.cms.scheduling.ScheduleMode.FIXED_TIME "
                    + "AND s.status = com.cms.scheduling.SlotStatus.OPEN "
                    + "AND s.session.sessionDate = :date "
                    + "AND s.session.sessionDate >= :from "
                    + "AND (:doctorProfileId IS NULL OR s.session.doctorProfile.id = :doctorProfileId) "
                    + "ORDER BY s.startTime ASC",
            countQuery = "SELECT COUNT(s) FROM Slot s "
                    + "WHERE s.session.clinic.id = :clinicId "
                    + "AND s.session.mode = com.cms.scheduling.ScheduleMode.FIXED_TIME "
                    + "AND s.status = com.cms.scheduling.SlotStatus.OPEN "
                    + "AND s.session.sessionDate = :date "
                    + "AND s.session.sessionDate >= :from "
                    + "AND (:doctorProfileId IS NULL OR s.session.doctorProfile.id = :doctorProfileId)")
    Page<Slot> findOpenFixedTimeSlotsOnDate(
            @Param("clinicId") UUID clinicId,
            @Param("doctorProfileId") UUID doctorProfileId,
            @Param("date") LocalDate date,
            @Param("from") LocalDate from,
            Pageable pageable);

    /**
     * 023-no-show-detection: candidate Slots for the automatic sweep - still BOOKED, not on
     * hold, Fixed-Time only (Clarifications). The grace-period elapsed check itself happens
     * in Java, not here (research.md - combining Session.sessionDate + Slot.startTime into a
     * single comparable instant isn't a clean single SQL predicate across this join).
     */
    @Query("SELECT s FROM Slot s "
            + "WHERE s.status = com.cms.scheduling.SlotStatus.BOOKED "
            + "AND s.onHold = false "
            + "AND s.session.mode = com.cms.scheduling.ScheduleMode.FIXED_TIME")
    List<Slot> findBookedFixedTimeCandidatesForNoShow();
}
