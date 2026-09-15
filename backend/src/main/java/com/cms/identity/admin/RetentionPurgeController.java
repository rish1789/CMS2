package com.cms.identity.admin;

import com.cms.clinical.RetentionPurgeService;
import com.cms.identity.admin.dto.RetentionPurgeResultResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implements contracts/retention-purge.md. Sits behind {@link SuperAdminSecurityConfig} - unlike
 * 037's own PatientAnonymizationController (staff-JWT gated, belongs in com.cms.patient.record),
 * this manual trigger genuinely IS Super-Admin-only per its own business rule, so this module's
 * existing Basic-Auth chain is the correct, matching authentication mechanism (research.md R8).
 * No additional role check is needed here - this chain's only valid identity is the Super Admin,
 * so successful authentication already is the authorization.
 */
@RestController
@RequestMapping("/api/v1/admin/retention-purge")
public class RetentionPurgeController {

    private final RetentionPurgeService retentionPurgeService;

    public RetentionPurgeController(RetentionPurgeService retentionPurgeService) {
        this.retentionPurgeService = retentionPurgeService;
    }

    @PostMapping("/run")
    public RetentionPurgeResultResponse run() {
        int purgedBookingCount = retentionPurgeService.purge();
        return new RetentionPurgeResultResponse(purgedBookingCount);
    }
}
