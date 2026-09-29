package com.cms.scheduling.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.repository.SlotRepository;
import com.cms.scheduling.service.SlotAutoCompletionService;
import com.cms.scheduling.service.SlotCompletionService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 057-day-sheet-status-overhaul: mirrors {@code NoShowDetectionServiceTest}'s boundary-testing
 * shape exactly - builds slot/session mock data far enough in the past/future relative to the
 * real current time to unambiguously land on either side of the "has the scheduled end time
 * passed" boundary (research.md Decision 2). Pure Mockito - no Spring context, no Docker.
 */
@ExtendWith(MockitoExtension.class)
class SlotAutoCompletionServiceTest {

    @Mock
    private SlotRepository slotRepository;

    @Mock
    private SlotCompletionService slotCompletionService;

    @Mock
    private Slot notYetEndedSlot;

    @Mock
    private Slot pastEndTimeSlot;

    @Mock
    private Session session;

    private SlotAutoCompletionService newService() {
        return new SlotAutoCompletionService(slotRepository, slotCompletionService);
    }

    @Test
    void slotNotYetPastItsScheduledEndTimeIsLeftUntouched() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endsInTwoHours = now.plusHours(2);
        when(notYetEndedSlot.getSession()).thenReturn(session);
        when(session.getSessionDate()).thenReturn(endsInTwoHours.toLocalDate());
        when(notYetEndedSlot.getEndTime()).thenReturn(endsInTwoHours.toLocalTime());
        when(slotRepository.findAppearedFixedTimeCandidatesForAutoCompletion()).thenReturn(List.of(notYetEndedSlot));

        int completed = newService().completeExpiredAppearedSlots();

        assertThat(completed).isZero();
        verify(slotCompletionService, never()).completeSlotAutomatically(notYetEndedSlot);
    }

    @Test
    void slotPastItsScheduledEndTimeIsCompletedAndCounted() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endedTwoHoursAgo = now.minusHours(2);
        when(pastEndTimeSlot.getSession()).thenReturn(session);
        when(session.getSessionDate()).thenReturn(endedTwoHoursAgo.toLocalDate());
        when(pastEndTimeSlot.getEndTime()).thenReturn(endedTwoHoursAgo.toLocalTime());
        when(slotRepository.findAppearedFixedTimeCandidatesForAutoCompletion()).thenReturn(List.of(pastEndTimeSlot));

        int completed = newService().completeExpiredAppearedSlots();

        assertThat(completed).isEqualTo(1);
        verify(slotCompletionService).completeSlotAutomatically(pastEndTimeSlot);
    }
}
