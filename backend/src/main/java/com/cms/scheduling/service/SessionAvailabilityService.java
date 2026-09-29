package com.cms.scheduling.service;

import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.SessionCancellation;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.repository.SessionCancellationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 065-phase1-stabilization (research.md R4, data-model.md "Derived rule"): the one bookability rule
 * every booking path and the waitlist offer path share. Returns a verdict only - each caller maps it
 * to its own module's exception, so this module never depends on {@code com.cms.booking}.
 *
 * <p>Precedence: PAST_DATE, then ELAPSED (timed slots only; a slot starting exactly now is still
 * bookable - owner decision 5), then CANCELLED (a whole-session record, or a range {@code [from, to)}
 * covering the probe time), else ACCEPTING. The probe is the slot's start for a timed slot, or the
 * current time for an untimed request (queue token, walk-in) - and for an untimed request only when
 * the session is today, since "now" says nothing about a future day's ranges.
 */
@Service
public class SessionAvailabilityService {

    private final SessionCancellationRepository cancellationRepository;
    private final Clock clock;

    // Explicit @Autowired: the same two-constructor pattern as SessionLiveStatusService - the second,
    // Clock-accepting constructor is for deterministic unit tests in com.cms.scheduling.unit.
    @Autowired
    public SessionAvailabilityService(SessionCancellationRepository cancellationRepository) {
        this(cancellationRepository, Clock.systemDefaultZone());
    }

    public SessionAvailabilityService(SessionCancellationRepository cancellationRepository, Clock clock) {
        this.cancellationRepository = cancellationRepository;
        this.clock = clock;
    }

    /** The server's current local date-time, from the same clock the verdict uses. */
    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /**
     * @param slot the timed slot being booked, or {@code null} (or an untimed slot) for a request that
     *     mints its own place - a queue token or a walk-in
     */
    @Transactional(readOnly = true)
    public Verdict evaluate(Session session, Slot slot) {
        LocalDateTime now = now();
        LocalDate sessionDate = session.getSessionDate();
        if (sessionDate.isBefore(now.toLocalDate())) {
            return Verdict.PAST_DATE;
        }

        boolean timed = slot != null && !slot.isUntimed();
        if (timed && LocalDateTime.of(sessionDate, slot.getStartTime()).isBefore(now)) {
            return Verdict.ELAPSED;
        }

        LocalTime probe = timed
                ? slot.getStartTime()
                : sessionDate.equals(now.toLocalDate()) ? now.toLocalTime() : null;

        List<SessionCancellation> cancellations = cancellationRepository.findBySession_Id(session.getId());
        for (SessionCancellation cancellation : cancellations) {
            if (cancellation.getFromTime() == null || (probe != null && covers(cancellation, probe))) {
                return Verdict.CANCELLED;
            }
        }
        return Verdict.ACCEPTING;
    }

    /** FR-009: only a whole-session record makes a repeat whole cancellation "already cancelled". */
    @Transactional(readOnly = true)
    public boolean isWholeCancelled(Session session) {
        return cancellationRepository.existsBySession_IdAndFromTimeIsNull(session.getId());
    }

    /**
     * FR-005: records a whole-session cancellation by {@code cancelledByAccountId}. Flushed
     * immediately so a concurrent second whole cancellation hits {@code uq_session_cancellation_whole}
     * here, as a {@link org.springframework.dao.DataIntegrityViolationException} the caller maps -
     * before it has touched any booking. Joins the caller's transaction (no annotation of its own), so
     * a later failure in the caller rolls the record back too.
     */
    public SessionCancellation recordWholeCancellation(Session session, UUID cancelledByAccountId) {
        return cancellationRepository.saveAndFlush(
                SessionCancellation.whole(session, cancelledByAccountId, Instant.now(clock)));
    }

    /** FR-005/FR-011: records a {@code [from, to)} range ({@code to} null = to the end of the session). */
    public SessionCancellation recordRangeCancellation(
            Session session, LocalTime from, LocalTime to, UUID cancelledByAccountId) {
        return cancellationRepository.saveAndFlush(
                SessionCancellation.range(session, from, to, cancelledByAccountId, Instant.now(clock)));
    }

    /** Spec 030's cutoff boundaries: inclusive from, exclusive to, a null to meaning "to the end". */
    private static boolean covers(SessionCancellation range, LocalTime probe) {
        return !probe.isBefore(range.getFromTime())
                && (range.getToTime() == null || probe.isBefore(range.getToTime()));
    }

    public enum Verdict {
        ACCEPTING,
        PAST_DATE,
        ELAPSED,
        CANCELLED
    }
}
