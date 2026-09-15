package com.cms.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 023 FR-007: runs the sweep every minute - the grace period is only 10 minutes, so a once-nightly cadence (015's own) would be far too infrequent (research.md). */
@Component
public class NoShowDetectionTrigger {

    private static final Logger log = LoggerFactory.getLogger(NoShowDetectionTrigger.class);

    private final NoShowDetectionService noShowDetectionService;

    public NoShowDetectionTrigger(NoShowDetectionService noShowDetectionService) {
        this.noShowDetectionService = noShowDetectionService;
    }

    @Scheduled(cron = "0 * * * * *")
    public void runDetectionSweep() {
        int markedCount = noShowDetectionService.detectAndMarkNoShows();
        if (markedCount > 0) {
            log.info("No-show detection sweep: {} Slot(s) marked NO_SHOW", markedCount);
        }
    }
}
