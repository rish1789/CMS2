package com.cms.scheduling.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.SessionCancellation;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.repository.SessionCancellationRepository;
import com.cms.scheduling.service.SessionAvailabilityService;
import com.cms.scheduling.service.SessionAvailabilityService.Verdict;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 065-phase1-stabilization (tasks.md T005, data-model.md "Derived rule"): the one bookability rule
 * every booking path shares. "Now" is a fixed {@link Clock} at 2026-09-29T10:00, so each case is
 * deterministic regardless of when it runs. Owner decision 5 (v41-design-review.md): a timed slot
 * starting exactly now is still bookable - only {@code start < now} is ELAPSED.
 */
@ExtendWith(MockitoExtension.class)
class SessionAvailabilityServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 29);
    private static final LocalTime NOW = LocalTime.of(10, 0);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(TODAY.atTime(NOW).atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    @Mock
    private SessionCancellationRepository cancellationRepository;

    private final UUID sessionId = UUID.randomUUID();

    private SessionAvailabilityService service() {
        return new SessionAvailabilityService(cancellationRepository, FIXED_CLOCK);
    }

    private Session sessionOn(LocalDate date) {
        Session session = mock(Session.class);
        lenient().when(session.getId()).thenReturn(sessionId);
        lenient().when(session.getSessionDate()).thenReturn(date);
        return session;
    }

    private Slot timedAt(Session session, LocalTime start) {
        return new Slot(session, start, start.plusMinutes(15));
    }

    private void cancellations(SessionCancellation... records) {
        lenient().when(cancellationRepository.findBySession_Id(sessionId)).thenReturn(List.of(records));
    }

    private SessionCancellation whole(Session session) {
        return SessionCancellation.whole(session, UUID.randomUUID(), Instant.EPOCH);
    }

    private SessionCancellation range(Session session, LocalTime from, LocalTime to) {
        return SessionCancellation.range(session, from, to, UUID.randomUUID(), Instant.EPOCH);
    }

    @Test
    void aPastSessionDateIsPastDate() {
        Session session = sessionOn(TODAY.minusDays(1));
        cancellations();

        assertThat(service().evaluate(session, timedAt(session, LocalTime.of(11, 0)))).isEqualTo(Verdict.PAST_DATE);
    }

    @Test
    void aTimedSlotStartingBeforeNowTodayIsElapsed() {
        Session session = sessionOn(TODAY);
        cancellations();

        assertThat(service().evaluate(session, timedAt(session, LocalTime.of(9, 45)))).isEqualTo(Verdict.ELAPSED);
    }

    @Test
    void aTimedSlotStartingExactlyNowIsStillAccepting() {
        Session session = sessionOn(TODAY);
        cancellations();

        assertThat(service().evaluate(session, timedAt(session, NOW))).isEqualTo(Verdict.ACCEPTING);
    }

    @Test
    void aTimedSlotStartingAfterNowTodayIsAccepting() {
        Session session = sessionOn(TODAY);
        cancellations();

        assertThat(service().evaluate(session, timedAt(session, LocalTime.of(10, 15)))).isEqualTo(Verdict.ACCEPTING);
    }

    @Test
    void anEarlierClockTimeTomorrowIsAccepting() {
        Session session = sessionOn(TODAY.plusDays(1));
        cancellations();

        assertThat(service().evaluate(session, timedAt(session, LocalTime.of(8, 0)))).isEqualTo(Verdict.ACCEPTING);
    }

    @Test
    void aWholeCancellationIsCancelled() {
        Session session = sessionOn(TODAY.plusDays(1));
        cancellations(whole(session));

        assertThat(service().evaluate(session, timedAt(session, LocalTime.of(11, 0)))).isEqualTo(Verdict.CANCELLED);
        assertThat(service().evaluate(session, null)).isEqualTo(Verdict.CANCELLED);
    }

    @Test
    void aRangeCoveringTheSlotStartIsCancelled() {
        Session session = sessionOn(TODAY.plusDays(1));
        cancellations(range(session, LocalTime.of(11, 0), LocalTime.of(12, 0)));

        assertThat(service().evaluate(session, timedAt(session, LocalTime.of(11, 0)))).isEqualTo(Verdict.CANCELLED);
        assertThat(service().evaluate(session, timedAt(session, LocalTime.of(11, 45)))).isEqualTo(Verdict.CANCELLED);
    }

    @Test
    void anOpenEndedRangeCoversEverythingFromItsStart() {
        Session session = sessionOn(TODAY.plusDays(1));
        cancellations(range(session, LocalTime.of(11, 0), null));

        assertThat(service().evaluate(session, timedAt(session, LocalTime.of(16, 0)))).isEqualTo(Verdict.CANCELLED);
    }

    @Test
    void aSlotBeforeTheRangeIsAccepting() {
        Session session = sessionOn(TODAY.plusDays(1));
        cancellations(range(session, LocalTime.of(11, 0), LocalTime.of(12, 0)));

        assertThat(service().evaluate(session, timedAt(session, LocalTime.of(10, 45)))).isEqualTo(Verdict.ACCEPTING);
    }

    @Test
    void aSlotAtTheRangesToTimeIsAccepting() {
        Session session = sessionOn(TODAY.plusDays(1));
        cancellations(range(session, LocalTime.of(11, 0), LocalTime.of(12, 0)));

        assertThat(service().evaluate(session, timedAt(session, LocalTime.of(12, 0)))).isEqualTo(Verdict.ACCEPTING);
    }

    @Test
    void anyOfSeveralRangesCancels() {
        Session session = sessionOn(TODAY.plusDays(1));
        cancellations(
                range(session, LocalTime.of(9, 0), LocalTime.of(9, 30)),
                range(session, LocalTime.of(14, 0), LocalTime.of(15, 0)));

        assertThat(service().evaluate(session, timedAt(session, LocalTime.of(14, 30)))).isEqualTo(Verdict.CANCELLED);
        assertThat(service().evaluate(session, timedAt(session, LocalTime.of(10, 0)))).isEqualTo(Verdict.ACCEPTING);
    }

    @Test
    void untimedTodayWithNowInsideARangeIsCancelled() {
        Session session = sessionOn(TODAY);
        cancellations(range(session, LocalTime.of(9, 30), LocalTime.of(11, 0)));

        assertThat(service().evaluate(session, null)).isEqualTo(Verdict.CANCELLED);
    }

    @Test
    void untimedTodayWithNowBeforeTheRangeIsAccepting() {
        Session session = sessionOn(TODAY);
        cancellations(range(session, LocalTime.of(11, 0), null));

        assertThat(service().evaluate(session, null)).isEqualTo(Verdict.ACCEPTING);
    }

    @Test
    void anUntimedWalkInSlotUsesTheCurrentTimeNotAStartTime() {
        Session session = sessionOn(TODAY);
        cancellations(range(session, LocalTime.of(9, 30), LocalTime.of(11, 0)));

        assertThat(service().evaluate(session, new Slot(session, 1))).isEqualTo(Verdict.CANCELLED);
    }

    @Test
    void untimedOnAFutureDateIgnoresRanges() {
        Session session = sessionOn(TODAY.plusDays(1));
        cancellations(range(session, LocalTime.of(0, 0), null));

        assertThat(service().evaluate(session, null)).isEqualTo(Verdict.ACCEPTING);
    }

    @Test
    void untimedOnAPastDateIsPastDate() {
        Session session = sessionOn(TODAY.minusDays(1));
        cancellations();

        assertThat(service().evaluate(session, null)).isEqualTo(Verdict.PAST_DATE);
    }

    @Test
    void nowComesFromTheInjectedClock() {
        assertThat(service().now()).isEqualTo(TODAY.atTime(NOW));
    }

    @Test
    void isWholeCancelledAsksForAWholeRecordOnly() {
        Session session = sessionOn(TODAY);
        when(cancellationRepository.existsBySession_IdAndFromTimeIsNull(sessionId)).thenReturn(true);

        assertThat(service().isWholeCancelled(session)).isTrue();
    }

    @Test
    void aWholeCancellationIsFlushedWithTheCallerAndTheClocksInstant() {
        Session session = sessionOn(TODAY);
        UUID accountId = UUID.randomUUID();
        when(cancellationRepository.saveAndFlush(any(SessionCancellation.class))).thenAnswer(inv -> inv.getArgument(0));

        SessionCancellation record = service().recordWholeCancellation(session, accountId);

        assertThat(record.getSession()).isSameAs(session);
        assertThat(record.getFromTime()).isNull();
        assertThat(record.getToTime()).isNull();
        assertThat(record.getCancelledByAccountId()).isEqualTo(accountId);
        assertThat(record.getCancelledAt()).isEqualTo(FIXED_CLOCK.instant());
        verify(cancellationRepository).saveAndFlush(record);
    }

    @Test
    void aConcurrentSecondWholeCancellationSurfacesTheUniqueViolation() {
        Session session = sessionOn(TODAY);
        when(cancellationRepository.saveAndFlush(any(SessionCancellation.class)))
                .thenThrow(new DataIntegrityViolationException("uq_session_cancellation_whole"));

        assertThatThrownBy(() -> service().recordWholeCancellation(session, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aRangeCancellationKeepsItsBounds() {
        Session session = sessionOn(TODAY);
        UUID accountId = UUID.randomUUID();
        when(cancellationRepository.saveAndFlush(any(SessionCancellation.class))).thenAnswer(inv -> inv.getArgument(0));

        SessionCancellation record =
                service().recordRangeCancellation(session, LocalTime.of(11, 0), LocalTime.of(12, 0), accountId);

        assertThat(record.getFromTime()).isEqualTo(LocalTime.of(11, 0));
        assertThat(record.getToTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(record.getCancelledByAccountId()).isEqualTo(accountId);
        assertThat(record.getCancelledAt()).isEqualTo(FIXED_CLOCK.instant());
    }
}
