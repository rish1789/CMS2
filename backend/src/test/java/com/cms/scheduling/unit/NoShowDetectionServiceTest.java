package com.cms.scheduling.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.scheduling.repository.SlotRepository;
import com.cms.scheduling.service.NoShowDetectionService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 048-backend-unit-tests US4: 023's 10-minute grace-period boundary - exactly the kind of
 * off-by-one/boundary logic most prone to silent regression. {@code detectAndMarkNoShows} calls
 * {@code LocalDateTime.now()} directly (no injectable Clock), so this test builds slot/session
 * mock data with scheduled times far enough in the past/future *relative to the real current
 * time* to unambiguously land on either side of the grace period (research.md Decision 3).
 * Pure Mockito - no Spring context, no Docker.
 */
@ExtendWith(MockitoExtension.class)
class NoShowDetectionServiceTest {

    @Mock
    private SlotRepository slotRepository;

    @Mock
    private Slot withinGraceSlot;

    @Mock
    private Slot pastGraceSlot;

    @Mock
    private Session session;

    private NoShowDetectionService newService() {
        return new NoShowDetectionService(slotRepository);
    }

    @Test
    void withinGracePeriodIsNotMarkedNoShow() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime scheduledTwoMinutesAgo = now.minusMinutes(2);
        when(withinGraceSlot.getSession()).thenReturn(session);
        when(session.getSessionDate()).thenReturn(scheduledTwoMinutesAgo.toLocalDate());
        when(withinGraceSlot.getStartTime()).thenReturn(scheduledTwoMinutesAgo.toLocalTime());
        when(slotRepository.findBookedFixedTimeCandidatesForNoShow()).thenReturn(List.of(withinGraceSlot));

        int marked = newService().detectAndMarkNoShows();

        assertThat(marked).isZero();
        verify(withinGraceSlot, never()).setStatus(SlotStatus.NO_SHOW);
        verify(slotRepository, never()).save(withinGraceSlot);
    }

    @Test
    void pastGracePeriodIsMarkedNoShowAndCounted() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime scheduledTwoHoursAgo = now.minusHours(2);
        when(pastGraceSlot.getSession()).thenReturn(session);
        when(session.getSessionDate()).thenReturn(scheduledTwoHoursAgo.toLocalDate());
        when(pastGraceSlot.getStartTime()).thenReturn(scheduledTwoHoursAgo.toLocalTime());
        when(slotRepository.findBookedFixedTimeCandidatesForNoShow()).thenReturn(List.of(pastGraceSlot));

        int marked = newService().detectAndMarkNoShows();

        assertThat(marked).isEqualTo(1);
        verify(pastGraceSlot).setStatus(SlotStatus.NO_SHOW);
        verify(slotRepository).save(pastGraceSlot);
    }
}
