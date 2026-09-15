package com.cms.identity.admin;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Super Admin console redesign: manual trigger for the rejected-record purge, mirroring
 * RetentionPurgeController's exact shape - the nightly {@link RejectedRecordPurgeTrigger} is the
 * normal path, this exists for on-demand ops use (and, unlike the Testcontainers-gated
 * integration suite, is exercisable without waiting for 2am).
 */
@RestController
@RequestMapping("/api/v1/admin/rejected-purge")
public class RejectedRecordPurgeController {

    private final RejectedRecordPurgeService rejectedRecordPurgeService;

    public RejectedRecordPurgeController(RejectedRecordPurgeService rejectedRecordPurgeService) {
        this.rejectedRecordPurgeService = rejectedRecordPurgeService;
    }

    @PostMapping("/run")
    public RejectedRecordPurgeService.PurgeResult run() {
        return rejectedRecordPurgeService.purge();
    }
}
