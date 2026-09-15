package com.cms.clinical;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 038 research.md R7: fires the retention purge monthly, midnight on the 1st - the same service/trigger split as {@code WaitlistExpirySweepTrigger} (032), just at monthly instead of per-minute cadence. */
@Component
public class RetentionPurgeTrigger {

    private static final Logger log = LoggerFactory.getLogger(RetentionPurgeTrigger.class);

    private final RetentionPurgeService retentionPurgeService;

    public RetentionPurgeTrigger(RetentionPurgeService retentionPurgeService) {
        this.retentionPurgeService = retentionPurgeService;
    }

    @Scheduled(cron = "0 0 0 1 * *")
    public void runRetentionPurge() {
        int purgedCount = retentionPurgeService.purge();
        if (purgedCount > 0) {
            log.info("Retention purge: {} booking(s) had clinical content purged", purgedCount);
        }
    }
}
