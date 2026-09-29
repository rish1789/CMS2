package com.cms.scheduling.service;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 057-day-sheet-status-overhaul: runs the auto-completion sweep every minute, mirroring {@link NoShowDetectionTrigger}'s exact cadence and shape. */
@Component
public class SlotAutoCompletionTrigger {

    private static final Logger log = LoggerFactory.getLogger(SlotAutoCompletionTrigger.class);

    private final SlotAutoCompletionService slotAutoCompletionService;

    public SlotAutoCompletionTrigger(SlotAutoCompletionService slotAutoCompletionService) {
        this.slotAutoCompletionService = slotAutoCompletionService;
    }

    @Scheduled(cron = "0 * * * * *")
    public void runAutoCompletionSweep() {
        int completedCount = slotAutoCompletionService.completeExpiredAppearedSlots();
        if (completedCount > 0) {
            log.info("Auto-completion sweep: {} Slot(s) marked COMPLETED", completedCount);
        }
    }
}
