package com.cms.protection.api;

import com.cms.identity.account.config.SecurityConfig;
import com.cms.protection.domain.SuspiciousActivityFlagStatus;
import com.cms.protection.dto.FlagDetailResponse;
import com.cms.protection.dto.FlagListResponse;
import com.cms.protection.dto.FlagResponse;
import com.cms.protection.service.ClinicProtectionFlagService;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 060-booking-abuse-prevention (contracts/booking-protection.md #2): ClinicAdmin flag review,
 * clinic-scoped (spec.md FR-021-FR-025). Staff realm - authorization itself lives in {@link
 * ClinicProtectionFlagService} (research.md Decision 7's reused ClinicAdmin gate), matching this
 * codebase's own established split between controller and service.
 */
@RestController
@RequestMapping("/api/v1/clinics/{clinicId}/protection/flags")
public class ClinicProtectionFlagController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ClinicProtectionFlagService flagService;

    public ClinicProtectionFlagController(ClinicProtectionFlagService flagService) {
        this.flagService = flagService;
    }

    @GetMapping
    public FlagListResponse list(
            @PathVariable UUID clinicId,
            @RequestParam(required = false) SuspiciousActivityFlagStatus status,
            @RequestParam(required = false) UUID patientAccountId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        SuspiciousActivityFlagStatus effectiveStatus = status != null ? status : SuspiciousActivityFlagStatus.OUTSTANDING;
        return flagService.list(callerAccountId, clinicId, effectiveStatus, patientAccountId, page, size);
    }

    @GetMapping("/{flagId}")
    public FlagDetailResponse detail(@PathVariable UUID clinicId, @PathVariable UUID flagId, Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        return flagService.detail(callerAccountId, clinicId, flagId);
    }

    @PostMapping("/{flagId}/resolve")
    public FlagResponse resolve(@PathVariable UUID clinicId, @PathVariable UUID flagId, Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        return flagService.resolve(callerAccountId, clinicId, flagId, callerAccountId.toString());
    }
}
