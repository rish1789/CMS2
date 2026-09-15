package com.cms.waitlist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 032 research.md R6: fires the expiry sweep every minute - the identical cadence and service/trigger split as {@code NoShowDetectionTrigger} (023). */
@Component
public class WaitlistExpirySweepTrigger {

    private static final Logger log = LoggerFactory.getLogger(WaitlistExpirySweepTrigger.class);

    private final WaitlistExpirySweepService waitlistExpirySweepService;

    public WaitlistExpirySweepTrigger(WaitlistExpirySweepService waitlistExpirySweepService) {
        this.waitlistExpirySweepService = waitlistExpirySweepService;
    }

    @Scheduled(cron = "0 * * * * *")
    public void runExpirySweep() {
        int releasedCount = waitlistExpirySweepService.sweepExpiredOffers();
        if (releasedCount > 0) {
            log.info("Waitlist expiry sweep: {} offer(s) released", releasedCount);
        }
    }
}
