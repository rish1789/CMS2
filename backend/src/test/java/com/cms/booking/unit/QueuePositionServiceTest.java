package com.cms.booking.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cms.booking.domain.Booking;
import com.cms.booking.service.QueuePositionService;
import com.cms.booking.service.QueuePositionService.QueuePosition;
import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.scheduling.repository.SlotRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 064-queue-send-in-complete (US2, FR-004/FR-005, tasks.md T011): in a Queue session a patient's
 * position counts only the tokens still waiting ahead of theirs, so it falls as staff send patients
 * in or tokens are cancelled - and it is not applicable once their own token is no longer waiting.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QueuePositionServiceTest {

    @Mock SlotRepository slotRepository;

    private Session session;
    private final List<Slot> tokens = new ArrayList<>();

    @BeforeEach
    void queueSession() {
        session = mock(Session.class);
        when(session.getId()).thenReturn(UUID.randomUUID());
        when(session.getMode()).thenReturn(ScheduleMode.QUEUE);
        when(slotRepository.findBySession_Id(session.getId())).thenReturn(tokens);
    }

    private Slot token(int number, SlotStatus status) {
        Slot slot = new Slot(session, number);
        slot.setStatus(status);
        tokens.add(slot);
        return slot;
    }

    private QueuePosition positionOf(Slot slot) {
        Booking booking = mock(Booking.class);
        when(booking.getSlot()).thenReturn(slot);
        return new QueuePositionService(slotRepository).positionOf(booking);
    }

    @Test
    void fourWaitingTokensArePositionsOneToFour() {
        List<Slot> waiting = List.of(
                token(1, SlotStatus.BOOKED), token(2, SlotStatus.BOOKED), token(3, SlotStatus.BOOKED), token(4, SlotStatus.BOOKED));

        for (int i = 0; i < waiting.size(); i++) {
            QueuePosition position = positionOf(waiting.get(i));
            assertThat(position.applicable()).isTrue();
            assertThat(position.position()).isEqualTo(i + 1);
        }
    }

    @Test
    void sendingTheFirstTokenInMovesEveryoneBehindItUp() {
        Slot first = token(1, SlotStatus.BOOKED);
        token(2, SlotStatus.BOOKED);
        token(3, SlotStatus.BOOKED);
        Slot fourth = token(4, SlotStatus.BOOKED);
        assertThat(positionOf(fourth).position()).isEqualTo(4);

        first.setStatus(SlotStatus.APPEARED);

        assertThat(positionOf(fourth).position()).isEqualTo(3);
    }

    @Test
    void aCancelledTokenAheadIsNotCounted() {
        token(1, SlotStatus.BOOKED);
        token(2, SlotStatus.OPEN); // its booking was cancelled; the token is never re-issued
        Slot mine = token(3, SlotStatus.BOOKED);

        assertThat(positionOf(mine).position()).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(value = SlotStatus.class, names = {"APPEARED", "COMPLETED", "OPEN", "NO_SHOW"})
    void aTokenThatIsNoLongerWaitingHasNoPosition(SlotStatus status) {
        token(1, SlotStatus.BOOKED);
        Slot mine = token(2, status);

        QueuePosition position = positionOf(mine);

        assertThat(position.applicable()).isFalse();
        assertThat(position.position()).isNull();
    }
}
