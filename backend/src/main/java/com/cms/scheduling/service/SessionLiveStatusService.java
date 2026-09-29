package com.cms.scheduling.service;

import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.scheduling.repository.SlotRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 061-doctor-live-status (spec.md BR-005-BR-010): computes live schedule status fresh, on every
 * call - never cached, never trigger-based (unlike {@link SessionDelayService}, left untouched
 * per spec Assumption A6). Applies only to Fixed-Time sessions (BR gate mirrors {@link
 * SessionDelayService}'s own FR-007 gate).
 *
 * <p>research.md Decision 3: authorization for the staff/doctor path reuses {@link
 * SessionDelayService#resolveAuthorizedSession}. {@link #liveStatusFor(Session)} is the
 * pure-computation half, callable directly once a caller (e.g. the patient-facing controller in
 * {@code com.cms.booking}) has already authorized access to the session by its own means
 * (booking ownership, not clinic/doctor scoping) - see research.md Decision 5.
 */
@Service
public class SessionLiveStatusService {

    private final SlotRepository slotRepository;
    private final SessionDelayService sessionDelayService;
    private final OperationalDayService operationalDayService;
    private final Clock clock;

    // Explicit @Autowired: mirrors RetentionPurgeService's own established pattern (a second,
    // Clock-accepting constructor for deterministic unit testing alongside this one) - Spring's
    // implicit single-constructor autowiring only applies with exactly one constructor present.
    // Public (not package-private like RetentionPurgeService's) so this module's own
    // `com.cms.scheduling.unit` test package - a sibling, not the same package - can use it.
    @Autowired
    public SessionLiveStatusService(
            SlotRepository slotRepository,
            SessionDelayService sessionDelayService,
            OperationalDayService operationalDayService) {
        this(slotRepository, sessionDelayService, operationalDayService, Clock.systemDefaultZone());
    }

    public SessionLiveStatusService(
            SlotRepository slotRepository,
            SessionDelayService sessionDelayService,
            OperationalDayService operationalDayService,
            Clock clock) {
        this.slotRepository = slotRepository;
        this.sessionDelayService = sessionDelayService;
        this.operationalDayService = operationalDayService;
        this.clock = clock;
    }

    /** Staff/doctor path: resolves + authorizes the session, then computes (FR-012). */
    @Transactional(readOnly = true)
    public LiveStatus liveStatus(UUID callerAccountId, UUID clinicId, UUID sessionId) {
        Session session = sessionDelayService.resolveAuthorizedSession(callerAccountId, clinicId, sessionId);
        return liveStatusFor(session);
    }

    /**
     * Pure computation, no authorization - the caller is responsible for having already
     * established the right to view this session (research.md Decision 5).
     */
    @Transactional(readOnly = true)
    public LiveStatus liveStatusFor(Session session) {
        if (session.getMode() != ScheduleMode.FIXED_TIME) {
            return new LiveStatus(false, null, null, null, null, null, null);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate operationalDay = operationalDayService.operationalDateOf(now);

        // BR-016: a session from a past operational day is no longer live, even if its remaining
        // slots were never resolved - otherwise it would report DELAYED indefinitely.
        if (session.getSessionDate().isBefore(operationalDay)) {
            return new LiveStatus(false, null, null, null, null, null, null);
        }

        List<Slot> allTimedSlots = slotRepository.findBySession_Id(session.getId()).stream()
                .filter(s -> s.getStartTime() != null)
                .sorted(Comparator.comparing(Slot::getStartTime))
                .toList();

        LocalTime firstSlotTime = allTimedSlots.isEmpty() ? null : allTimedSlots.get(0).getStartTime();

        // BR-005: OPEN slots (never booked, or booked then cancelled) never participate.
        List<Slot> participating =
                allTimedSlots.stream().filter(s -> s.getStatus() != SlotStatus.OPEN).toList();

        if (participating.isEmpty()) {
            // Nothing has ever been booked into this session yet - nothing to compare against.
            return new LiveStatus(true, Status.NOT_STARTED, null, null, null, firstSlotTime, operationalDay);
        }

        // BR-006: actual pointer - earliest participating slot not yet resolved.
        int actualIndex = -1;
        for (int i = 0; i < participating.size(); i++) {
            SlotStatus status = participating.get(i).getStatus();
            if (status != SlotStatus.COMPLETED && status != SlotStatus.NO_SHOW) {
                actualIndex = i;
                break;
            }
        }

        // BR-007: expected pointer - last participating slot whose scheduled time has arrived.
        int expectedIndex = -1;
        for (int i = 0; i < participating.size(); i++) {
            LocalDateTime scheduled = LocalDateTime.of(session.getSessionDate(), participating.get(i).getStartTime());
            if (!scheduled.isAfter(now)) {
                expectedIndex = i;
            } else {
                break;
            }
        }

        if (actualIndex == -1) {
            // Every participating slot is resolved - nothing left to see (BR-003).
            return new LiveStatus(true, Status.COMPLETED, null, null, null, firstSlotTime, operationalDay);
        }

        if (expectedIndex == -1) {
            // Now is before the first participating slot's own scheduled time (BR-002).
            return new LiveStatus(true, Status.NOT_STARTED, null, null, null, firstSlotTime, operationalDay);
        }

        int currentOrdinal = actualIndex + 1;
        int expectedOrdinal = expectedIndex + 1;

        if (actualIndex == expectedIndex) {
            return new LiveStatus(true, Status.ON_TIME, currentOrdinal, expectedOrdinal, null, firstSlotTime, operationalDay);
        }

        // BR-008: deviation is always a comparison of two schedule positions, never now-minus-first-slot.
        LocalTime actualSlotTime = participating.get(actualIndex).getStartTime();
        LocalTime expectedSlotTime = participating.get(expectedIndex).getStartTime();
        int deviationMinutes = (int)
                Math.abs(Duration.between(actualSlotTime, expectedSlotTime).toMinutes());

        Status status = actualIndex < expectedIndex ? Status.DELAYED : Status.RUNNING_EARLY;
        return new LiveStatus(true, status, currentOrdinal, expectedOrdinal, deviationMinutes, firstSlotTime, operationalDay);
    }

    /**
     * FR-010: the caller's own slot's scheduled time, adjusted by the session's current
     * deviation - {@code null} once that slot is already resolved (nothing left to wait for).
     * Uses this same service's {@link #clock}, per BR-010 - one consistent server-side time
     * source, not a second {@code LocalDateTime.now()} call scattered into the caller.
     */
    public Integer estimatedWaitMinutesFor(Session session, Slot patientSlot, LiveStatus status) {
        if (patientSlot.getStatus() == SlotStatus.COMPLETED || patientSlot.getStatus() == SlotStatus.NO_SHOW) {
            return null;
        }
        if (patientSlot.getStartTime() == null || status.status() == null) {
            return null;
        }

        LocalDateTime scheduled = LocalDateTime.of(session.getSessionDate(), patientSlot.getStartTime());
        int deviation = status.deviationMinutes() == null ? 0 : status.deviationMinutes();
        LocalDateTime estimated =
                switch (status.status()) {
                    case DELAYED -> scheduled.plusMinutes(deviation);
                    case RUNNING_EARLY -> scheduled.minusMinutes(deviation);
                    default -> scheduled;
                };

        long minutes = Duration.between(LocalDateTime.now(clock), estimated).toMinutes();
        return (int) Math.max(0, minutes);
    }

    public enum Status {
        NOT_STARTED,
        ON_TIME,
        RUNNING_EARLY,
        DELAYED,
        COMPLETED
    }

    public record LiveStatus(
            boolean applicable,
            Status status,
            Integer currentPatientOrdinal,
            Integer expectedPatientOrdinal,
            Integer deviationMinutes,
            LocalTime firstSlotTime,
            LocalDate operationalDay) {}
}
