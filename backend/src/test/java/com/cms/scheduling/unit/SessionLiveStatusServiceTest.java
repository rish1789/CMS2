package com.cms.scheduling.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.scheduling.repository.SlotRepository;
import com.cms.scheduling.service.OperationalDayService;
import com.cms.scheduling.service.SessionDelayService;
import com.cms.scheduling.service.SessionLiveStatusService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 061-doctor-live-status (spec.md BR-005-BR-010): pure calculation coverage for {@link
 * SessionLiveStatusService#liveStatusFor(Session)} - no Spring context, no DB. Mirrors the
 * scenario list agreed during /speckit-analyze remediation (tasks.md T005).
 *
 * <p>"Now" is a fixed {@link Clock} at 2026-09-23T09:30:00, not the real wall clock - every
 * scenario below is expressed relative to that fixed instant, so the tests are deterministic
 * regardless of when they actually run.
 */
@ExtendWith(MockitoExtension.class)
class SessionLiveStatusServiceTest {

    private static final LocalDate SESSION_DATE = LocalDate.of(2026, 9, 23);
    private static final LocalTime NOW = LocalTime.of(9, 30);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(SESSION_DATE.atTime(NOW).atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    @Mock
    private SlotRepository slotRepository;

    @Mock
    private SessionDelayService sessionDelayService;

    private final OperationalDayService operationalDayService = new OperationalDayService();

    private final UUID sessionId = UUID.randomUUID();

    private SessionLiveStatusService service() {
        return new SessionLiveStatusService(slotRepository, sessionDelayService, operationalDayService, FIXED_CLOCK);
    }

    private Session fixedTimeSession() {
        Session session = mock(Session.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.getMode()).thenReturn(ScheduleMode.FIXED_TIME);
        // lenient: unused when every slot is OPEN (participating list empty, returned before the
        // expected-pointer loop ever reads sessionDate) - e.g. noBookingsAtAllIsNotStartedRegardlessOfTime.
        lenient().when(session.getSessionDate()).thenReturn(SESSION_DATE);
        return session;
    }

    /** Builds a Fixed-Time slot with the given scheduled start time and status, 15 minutes long. */
    private Slot slotAt(Session session, LocalTime start, SlotStatus status) {
        Slot slot = new Slot(session, start, start.plusMinutes(15));
        slot.setStatus(status);
        return slot;
    }

    @Test
    void queueModeSessionIsNotApplicable() {
        Session session = mock(Session.class);
        when(session.getMode()).thenReturn(ScheduleMode.QUEUE);

        SessionLiveStatusService.LiveStatus result = service().liveStatusFor(session);

        assertThat(result.applicable()).isFalse();
        assertThat(result.status()).isNull();
    }

    @Test
    void beforeTheFirstSlotsTimeIsNotStarted() {
        Session session = fixedTimeSession();
        List<Slot> slots = List.of(
                slotAt(session, NOW.plusMinutes(30), SlotStatus.BOOKED), slotAt(session, NOW.plusMinutes(45), SlotStatus.BOOKED));
        when(slotRepository.findBySession_Id(sessionId)).thenReturn(slots);

        SessionLiveStatusService.LiveStatus result = service().liveStatusFor(session);

        assertThat(result.applicable()).isTrue();
        assertThat(result.status()).isEqualTo(SessionLiveStatusService.Status.NOT_STARTED);
        assertThat(result.currentPatientOrdinal()).isNull();
        assertThat(result.expectedPatientOrdinal()).isNull();
        assertThat(result.deviationMinutes()).isNull();
        assertThat(result.firstSlotTime()).isEqualTo(NOW.plusMinutes(30));
    }

    @Test
    void noBookingsAtAllIsNotStartedRegardlessOfTime() {
        Session session = fixedTimeSession();
        List<Slot> slots = List.of(
                slotAt(session, NOW.minusMinutes(30), SlotStatus.OPEN), slotAt(session, NOW.minusMinutes(15), SlotStatus.OPEN));
        when(slotRepository.findBySession_Id(sessionId)).thenReturn(slots);

        SessionLiveStatusService.LiveStatus result = service().liveStatusFor(session);

        assertThat(result.status()).isEqualTo(SessionLiveStatusService.Status.NOT_STARTED);
    }

    @Test
    void actualAndExpectedOnTheSameSlotIsOnTime() {
        Session session = fixedTimeSession();
        List<Slot> slots = List.of(
                slotAt(session, NOW.minusMinutes(30), SlotStatus.COMPLETED),
                slotAt(session, NOW.minusMinutes(15), SlotStatus.COMPLETED),
                slotAt(session, NOW, SlotStatus.BOOKED),
                slotAt(session, NOW.plusMinutes(15), SlotStatus.BOOKED));
        when(slotRepository.findBySession_Id(sessionId)).thenReturn(slots);

        SessionLiveStatusService.LiveStatus result = service().liveStatusFor(session);

        assertThat(result.status()).isEqualTo(SessionLiveStatusService.Status.ON_TIME);
        assertThat(result.currentPatientOrdinal()).isEqualTo(3);
        assertThat(result.expectedPatientOrdinal()).isEqualTo(3);
        assertThat(result.deviationMinutes()).isNull();
    }

    @Test
    void actualBehindExpectedIsDelayed() {
        Session session = fixedTimeSession();
        // Doctor is still on slot 1 (BOOKED, never resolved) while slots up to "now" (slot 3) have arrived.
        List<Slot> slots = List.of(
                slotAt(session, NOW.minusMinutes(30), SlotStatus.BOOKED),
                slotAt(session, NOW.minusMinutes(15), SlotStatus.BOOKED),
                slotAt(session, NOW, SlotStatus.BOOKED));
        when(slotRepository.findBySession_Id(sessionId)).thenReturn(slots);

        SessionLiveStatusService.LiveStatus result = service().liveStatusFor(session);

        assertThat(result.status()).isEqualTo(SessionLiveStatusService.Status.DELAYED);
        assertThat(result.currentPatientOrdinal()).isEqualTo(1);
        assertThat(result.expectedPatientOrdinal()).isEqualTo(3);
        assertThat(result.deviationMinutes()).isEqualTo(30);
    }

    @Test
    void actualAheadOfExpectedIsRunningEarly() {
        Session session = fixedTimeSession();
        // Only slot 1's time has arrived ("expected" = slot 1), but slots 1-4 are already completed/booked.
        List<Slot> slots = List.of(
                slotAt(session, NOW, SlotStatus.COMPLETED),
                slotAt(session, NOW.plusMinutes(15), SlotStatus.COMPLETED),
                slotAt(session, NOW.plusMinutes(30), SlotStatus.COMPLETED),
                slotAt(session, NOW.plusMinutes(45), SlotStatus.BOOKED));
        when(slotRepository.findBySession_Id(sessionId)).thenReturn(slots);

        SessionLiveStatusService.LiveStatus result = service().liveStatusFor(session);

        assertThat(result.status()).isEqualTo(SessionLiveStatusService.Status.RUNNING_EARLY);
        assertThat(result.currentPatientOrdinal()).isEqualTo(4);
        assertThat(result.expectedPatientOrdinal()).isEqualTo(1);
        assertThat(result.deviationMinutes()).isEqualTo(45);
    }

    @Test
    void everyParticipatingSlotResolvedIsCompleted() {
        Session session = fixedTimeSession();
        List<Slot> slots = List.of(
                slotAt(session, NOW.minusMinutes(30), SlotStatus.COMPLETED),
                slotAt(session, NOW.minusMinutes(15), SlotStatus.NO_SHOW),
                slotAt(session, NOW.minusMinutes(5), SlotStatus.COMPLETED));
        when(slotRepository.findBySession_Id(sessionId)).thenReturn(slots);

        SessionLiveStatusService.LiveStatus result = service().liveStatusFor(session);

        assertThat(result.status()).isEqualTo(SessionLiveStatusService.Status.COMPLETED);
        assertThat(result.currentPatientOrdinal()).isNull();
        assertThat(result.expectedPatientOrdinal()).isNull();
    }

    @Test
    void aNoShowSlotCountsAsResolvedNotAsOngoingDelay() {
        // Doctor moved past a no-show patient onto the next one - the actual pointer should land
        // on the next BOOKED slot, not get stuck "on" the no-show slot forever.
        Session session = fixedTimeSession();
        List<Slot> slots =
                List.of(slotAt(session, NOW.minusMinutes(15), SlotStatus.NO_SHOW), slotAt(session, NOW, SlotStatus.BOOKED));
        when(slotRepository.findBySession_Id(sessionId)).thenReturn(slots);

        SessionLiveStatusService.LiveStatus result = service().liveStatusFor(session);

        assertThat(result.status()).isEqualTo(SessionLiveStatusService.Status.ON_TIME);
        assertThat(result.currentPatientOrdinal()).isEqualTo(2);
    }

    @Test
    void aBreakGapNeverCountsAsExpectedOrActualProgression() {
        // A break has no Slot rows at all - the gap between these two times is not a slot, so
        // nothing in that window is ever "expected."
        Session session = fixedTimeSession();
        List<Slot> slots = List.of(
                slotAt(session, NOW.minusMinutes(45), SlotStatus.COMPLETED),
                slotAt(session, NOW.minusMinutes(5), SlotStatus.COMPLETED));
        when(slotRepository.findBySession_Id(sessionId)).thenReturn(slots);

        SessionLiveStatusService.LiveStatus result = service().liveStatusFor(session);

        // Both slots resolved and before "now" -> COMPLETED, proving the gap between them was
        // simply skipped rather than treated as a missing/overdue appointment.
        assertThat(result.status()).isEqualTo(SessionLiveStatusService.Status.COMPLETED);
    }

    @Test
    void aCancelledSlotRevertedToOpenNeverChangesTheDeviationFigure_beforeAndAfterComparison() {
        // SC-005: cancelling a still-pending slot must not move the delay/early figure at all -
        // asserted here as a genuine before/after comparison, not just end-state correctness.
        Session session = fixedTimeSession();
        Slot stillBookedCurrent = slotAt(session, NOW.minusMinutes(30), SlotStatus.BOOKED);
        Slot slotAboutToBeCancelled = slotAt(session, NOW.minusMinutes(15), SlotStatus.BOOKED);
        Slot expectedSlot = slotAt(session, NOW, SlotStatus.BOOKED);
        List<Slot> slots = List.of(stillBookedCurrent, slotAboutToBeCancelled, expectedSlot);
        when(slotRepository.findBySession_Id(sessionId)).thenReturn(slots);

        SessionLiveStatusService.LiveStatus beforeResult = service().liveStatusFor(session);

        // Cancel the middle slot - it reverts to OPEN (existing BookingCancellationService behavior).
        slotAboutToBeCancelled.setStatus(SlotStatus.OPEN);
        SessionLiveStatusService.LiveStatus afterResult = service().liveStatusFor(session);

        assertThat(afterResult.status()).isEqualTo(beforeResult.status());
        assertThat(afterResult.deviationMinutes()).isEqualTo(beforeResult.deviationMinutes());
        assertThat(afterResult.currentPatientOrdinal()).isEqualTo(beforeResult.currentPatientOrdinal());
    }

    @Test
    void differentSlotIntervalsAreRespectedNotHardcoded() {
        // A 60-minute gap between slots, not the usual 15 - the deviation figure must still be
        // correct using each session's own real slot times, never an assumed interval.
        Session session = fixedTimeSession();
        List<Slot> slots =
                List.of(slotAt(session, NOW.minusMinutes(60), SlotStatus.BOOKED), slotAt(session, NOW, SlotStatus.BOOKED));
        when(slotRepository.findBySession_Id(sessionId)).thenReturn(slots);

        SessionLiveStatusService.LiveStatus result = service().liveStatusFor(session);

        assertThat(result.status()).isEqualTo(SessionLiveStatusService.Status.DELAYED);
        assertThat(result.deviationMinutes()).isEqualTo(60);
    }

    // BR-016 (2026-09-24 clarification): a session from a past operational day is no longer live,
    // even if staff never resolved its remaining slots - otherwise it reports DELAYED forever.
    @Test
    void aSessionFromAPastOperationalDayIsNotApplicableEvenWithUnresolvedSlots() {
        Session session = mock(Session.class);
        lenient().when(session.getId()).thenReturn(sessionId);
        when(session.getMode()).thenReturn(ScheduleMode.FIXED_TIME);
        when(session.getSessionDate()).thenReturn(SESSION_DATE.minusDays(1));
        lenient()
                .when(slotRepository.findBySession_Id(sessionId))
                .thenReturn(List.of(
                        slotAt(session, LocalTime.of(21, 35), SlotStatus.COMPLETED),
                        slotAt(session, LocalTime.of(21, 40), SlotStatus.BOOKED)));

        SessionLiveStatusService.LiveStatus result = service().liveStatusFor(session);

        assertThat(result.applicable()).isFalse();
        assertThat(result.status()).isNull();
    }

    @Test
    void yesterdaysSessionIsStillLiveAfterMidnightUntilTheOperationalDayBoundary() {
        Clock twoAmToday =
                Clock.fixed(SESSION_DATE.atTime(2, 0).atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        Session session = mock(Session.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.getMode()).thenReturn(ScheduleMode.FIXED_TIME);
        when(session.getSessionDate()).thenReturn(SESSION_DATE.minusDays(1));
        when(slotRepository.findBySession_Id(sessionId))
                .thenReturn(List.of(
                        slotAt(session, LocalTime.of(23, 30), SlotStatus.COMPLETED),
                        slotAt(session, LocalTime.of(23, 45), SlotStatus.BOOKED)));

        SessionLiveStatusService.LiveStatus result =
                new SessionLiveStatusService(slotRepository, sessionDelayService, operationalDayService, twoAmToday)
                        .liveStatusFor(session);

        assertThat(result.applicable()).isTrue();
        assertThat(result.status()).isEqualTo(SessionLiveStatusService.Status.ON_TIME);
        assertThat(result.operationalDay()).isEqualTo(SESSION_DATE.minusDays(1));
    }
}
