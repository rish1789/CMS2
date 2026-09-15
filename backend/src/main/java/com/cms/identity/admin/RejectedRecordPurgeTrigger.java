package com.cms.identity.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Super Admin console redesign: fires the rejected-record purge nightly at 2am - a daily
 * cadence, not RetentionPurgeTrigger's monthly one, since the retention window here is
 * configured in days (default 30) rather than years and a day-late purge would be a much more
 * noticeable gap.
 */
@Component
public class RejectedRecordPurgeTrigger {

    private static final Logger log = LoggerFactory.getLogger(RejectedRecordPurgeTrigger.class);

    private final RejectedRecordPurgeService rejectedRecordPurgeService;

    public RejectedRecordPurgeTrigger(RejectedRecordPurgeService rejectedRecordPurgeService) {
        this.rejectedRecordPurgeService = rejectedRecordPurgeService;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void runRejectedRecordPurge() {
        RejectedRecordPurgeService.PurgeResult result = rejectedRecordPurgeService.purge();
        if (result.clinicsPurged() > 0 || result.doctorsPurged() > 0) {
            log.info(
                    "Rejected-record purge: {} clinic(s), {} doctor profile(s) permanently deleted",
                    result.clinicsPurged(),
                    result.doctorsPurged());
        }
    }
}
