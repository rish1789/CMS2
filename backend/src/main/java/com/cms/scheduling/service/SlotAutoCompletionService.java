package com.cms.scheduling.service;

import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.repository.SlotRepository;


import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/**
 * 057-day-sheet-status-overhaul: the automatic completion sweep - structurally identical to
 * {@link NoShowDetectionService} (research.md Decision 2), deliberately NOT {@code
 * @Transactional} at all, calling {@link SlotCompletionService#completeSlotAutomatically} (which
 * has its own {@code @Transactional}, one per candidate) rather than any same-class helper. That
 * exact self-invocation {@code @Transactional}-bypass shape has already caused real bugs twice
 * in this codebase's history (020, 022's first implementation attempt) - avoided here the same
 * way {@link NoShowDetectionService} avoids it.
 */
@Service
public class SlotAutoCompletionService {

    private final SlotRepository slotRepository;
    private final SlotCompletionService slotCompletionService;

    public SlotAutoCompletionService(SlotRepository slotRepository, SlotCompletionService slotCompletionService) {
        this.slotRepository = slotRepository;
        this.slotCompletionService = slotCompletionService;
    }

    /** Completes each eligible candidate; returns the count completed. */
    public int completeExpiredAppearedSlots() {
        LocalDateTime now = LocalDateTime.now();
        int completedCount = 0;

        for (Slot slot : slotRepository.findAppearedFixedTimeCandidatesForAutoCompletion()) {
            LocalDateTime scheduledEnd = LocalDateTime.of(slot.getSession().getSessionDate(), slot.getEndTime());
            if (!scheduledEnd.isAfter(now)) {
                slotCompletionService.completeSlotAutomatically(slot);
                completedCount++;
            }
        }

        return completedCount;
    }
}
