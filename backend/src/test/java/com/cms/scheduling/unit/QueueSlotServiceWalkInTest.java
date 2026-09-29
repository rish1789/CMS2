package com.cms.scheduling.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.scheduling.exception.NotAFixedTimeSessionException;
import com.cms.scheduling.exception.NotAQueueSessionException;
import com.cms.scheduling.repository.SessionRepository;
import com.cms.scheduling.repository.SlotRepository;
import com.cms.scheduling.service.QueueSlotService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 063-front-desk-walk-in (research.md Decision 1, tasks.md T004): a Fixed-Time session's walk-in
 * line reuses the existing token issuance - same per-session counter, same unique index - rather
 * than a second queue system. The two entry points stay mode-exclusive.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QueueSlotServiceWalkInTest {

    @Mock SessionRepository sessionRepository;
    @Mock SlotRepository slotRepository;

    private final UUID sessionId = UUID.randomUUID();
    private Session session;

    @BeforeEach
    void setUp() {
        session = mock(Session.class);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(slotRepository.save(any(Slot.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private QueueSlotService service() {
        return new QueueSlotService(sessionRepository, slotRepository);
    }

    @Test
    void theFirstWalkInOfAFixedTimeSessionIsAnUntimedTokenOne() {
        when(session.getMode()).thenReturn(ScheduleMode.FIXED_TIME);
        when(slotRepository.findMaxTokenNumberBySession_Id(sessionId)).thenReturn(Optional.empty());

        Slot slot = service().issueNextWalkInSlot(sessionId);

        assertThat(slot.getTokenNumber()).isEqualTo(1);
        assertThat(slot.isUntimed()).isTrue();
        // 064-queue-send-in-complete (FR-001): minted as waiting, ready to be sent in.
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.BOOKED);
        assertThat(slot.getSession()).isSameAs(session);
    }

    @Test
    void theNextWalkInContinuesTheSessionsOwnTokenCounter() {
        when(session.getMode()).thenReturn(ScheduleMode.FIXED_TIME);
        when(slotRepository.findMaxTokenNumberBySession_Id(sessionId)).thenReturn(Optional.of(2));

        assertThat(service().issueNextWalkInSlot(sessionId).getTokenNumber()).isEqualTo(3);
    }

    /** 064-queue-send-in-complete (FR-001): a booked queue token is waiting, not open, from the moment it is issued. */
    @Test
    void aQueueTokenIsMintedWaiting() {
        when(session.getMode()).thenReturn(ScheduleMode.QUEUE);
        when(slotRepository.findMaxTokenNumberBySession_Id(sessionId)).thenReturn(Optional.of(4));

        Slot token = service().issueNextSlot(sessionId);

        assertThat(token.getTokenNumber()).isEqualTo(5);
        assertThat(token.getStatus()).isEqualTo(SlotStatus.BOOKED);
    }

    @Test
    void aWalkInSlotCannotBeIssuedIntoAQueueSession() {
        when(session.getMode()).thenReturn(ScheduleMode.QUEUE);

        assertThatThrownBy(() -> service().issueNextWalkInSlot(sessionId)).isInstanceOf(NotAFixedTimeSessionException.class);
    }

    @Test
    void theExistingQueueTokenStillRefusesAFixedTimeSession() {
        when(session.getMode()).thenReturn(ScheduleMode.FIXED_TIME);

        assertThatThrownBy(() -> service().issueNextSlot(sessionId)).isInstanceOf(NotAQueueSessionException.class);
    }
}
