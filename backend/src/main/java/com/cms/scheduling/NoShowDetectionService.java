package com.cms.scheduling;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/**
 * 023: the automatic no-show sweep - see research.md for why this is deliberately NOT
 * {@code @Transactional} at all, and calls {@link SlotRepository#save} directly per
 * candidate rather than through any same-class helper. That exact self-invocation
 * {@code @Transactional}-bypass shape already caused real bugs twice this session (020,
 * 022's first implementation attempt) - avoided here by having nothing to bypass.
 */
@Service
public class NoShowDetectionService {

    private static final int GRACE_PERIOD_MINUTES = 10;

    private final SlotRepository slotRepository;

    public NoShowDetectionService(SlotRepository slotRepository) {
        this.slotRepository = slotRepository;
    }

    /** FR-001..FR-003/FR-006: marks each eligible candidate NO_SHOW; returns the count marked. */
    public int detectAndMarkNoShows() {
        LocalDateTime now = LocalDateTime.now();
        int markedCount = 0;

        for (Slot slot : slotRepository.findBookedFixedTimeCandidatesForNoShow()) {
            LocalDateTime scheduledAt = LocalDateTime.of(slot.getSession().getSessionDate(), slot.getStartTime());
            if (scheduledAt.plusMinutes(GRACE_PERIOD_MINUTES).isBefore(now)) {
                slot.setStatus(SlotStatus.NO_SHOW);
                slotRepository.save(slot);
                markedCount++;
            }
        }

        return markedCount;
    }
}
