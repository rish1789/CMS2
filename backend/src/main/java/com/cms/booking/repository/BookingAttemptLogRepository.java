package com.cms.booking.repository;

import com.cms.booking.domain.BookingAttemptLog;
import com.cms.booking.domain.BookingAttemptOutcome;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 060-booking-abuse-prevention: backs both the rate-limit check (FR-007-FR-012) and the
 * high-attempt-volume/repeated-rate-limit-violation admin-flagging signals (FR-016, FR-020).
 */
public interface BookingAttemptLogRepository extends JpaRepository<BookingAttemptLog, UUID> {

    /**
     * FR-007: raw attempt count within a rolling window, whatever the outcome (FR-008). Shared by
     * both the rate-limit check (caller passes the rate-limit window) and FlagDetectionService's
     * high-attempt-volume signal (caller passes that signal's own, independently-configured
     * window - FR-016).
     */
    long countByPatientAccount_IdAndAttemptedAtAfter(UUID patientAccountId, Instant since);

    /**
     * research.md Decision 6/Clarifications: the most recent attempt NOT rated RATE_LIMITED -
     * the boundary before the current, possibly-still-ongoing cooldown episode began.
     */
    Optional<BookingAttemptLog> findFirstByPatientAccount_IdAndOutcomeNotOrderByAttemptedAtDesc(
            UUID patientAccountId, BookingAttemptOutcome outcome);

    /**
     * Paired with the query above: the earliest RATE_LIMITED row after that boundary - the true,
     * fixed trigger point of the current cooldown (Clarifications - further attempts during an
     * active cooldown never move this).
     */
    Optional<BookingAttemptLog> findFirstByPatientAccount_IdAndOutcomeAndAttemptedAtAfterOrderByAttemptedAtAsc(
            UUID patientAccountId, BookingAttemptOutcome outcome, Instant after);

    /** FR-020: every RATE_LIMITED row in a signal's own window - FlagDetectionService groups these into distinct cooldown episodes. */
    List<BookingAttemptLog> findByPatientAccount_IdAndOutcomeAndAttemptedAtAfterOrderByAttemptedAtAsc(
            UUID patientAccountId, BookingAttemptOutcome outcome, Instant since);

    /** FR-016: every (patient, clinic) pair with attempt volume since a given time, grouped - FlagDetectionService filters for count >= the signal's own threshold. */
    @Query("SELECT a.patientAccount.id AS patientAccountId, a.clinic.id AS clinicId, COUNT(a) AS count "
            + "FROM BookingAttemptLog a WHERE a.attemptedAt >= :since GROUP BY a.patientAccount.id, a.clinic.id")
    List<PatientClinicCount> countAttemptsByPatientAndClinicSince(@Param("since") Instant since);

    /** FR-020: every (patient, clinic) pair with a RATE_LIMITED outcome since a given time - FlagDetectionService groups the raw rows into distinct cooldown episodes. */
    @Query("SELECT a FROM BookingAttemptLog a WHERE a.outcome = com.cms.booking.domain.BookingAttemptOutcome.RATE_LIMITED "
            + "AND a.attemptedAt >= :since ORDER BY a.patientAccount.id, a.clinic.id, a.attemptedAt ASC")
    List<BookingAttemptLog> findRateLimitedSince(@Param("since") Instant since);

    /** FR-021: recent rate-limit violations for one clinic-scoped flag-review evidence panel. */
    List<BookingAttemptLog> findTop10ByPatientAccount_IdAndClinic_IdAndOutcomeOrderByAttemptedAtDesc(
            UUID patientAccountId, UUID clinicId, BookingAttemptOutcome outcome);

    /** spec.md SEC-005: rows older than the longest currently-active window age out - never retained indefinitely, unlike the audit-history tables (data-model.md). */
    long deleteByAttemptedAtBefore(Instant cutoff);

    /** Real patient activity at a clinic - blocks permanently deleting a rejected clinic. */
    long countByClinic_Id(UUID clinicId);

    interface PatientClinicCount {
        UUID getPatientAccountId();

        UUID getClinicId();

        long getCount();
    }
}
