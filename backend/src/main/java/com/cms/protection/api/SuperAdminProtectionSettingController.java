package com.cms.protection.api;

import com.cms.identity.admin.config.SuperAdminSecurityConfig;
import com.cms.protection.dto.SettingHistoryEntryResponse;
import com.cms.protection.dto.UpdateSettingRequest;
import com.cms.protection.service.ProtectionSettingService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 060-booking-abuse-prevention (contracts/booking-protection.md #4, spec.md FR-026/FR-027/US4):
 * this system's first runtime-editable admin settings surface. Super Admin realm - covered by
 * {@code SuperAdminSecurityConfig}'s existing broad {@code anyRequest().authenticated()} gate
 * (research.md Decision 7), no new matcher needed.
 */
@RestController
@RequestMapping("/api/v1/admin/protection-settings")
public class SuperAdminProtectionSettingController {

    private final ProtectionSettingService protectionSettingService;

    public SuperAdminProtectionSettingController(ProtectionSettingService protectionSettingService) {
        this.protectionSettingService = protectionSettingService;
    }

    @GetMapping
    public List<ProtectionSettingService.SettingView> list() {
        return protectionSettingService.listAll();
    }

    @PutMapping("/{name}")
    public ProtectionSettingService.SettingView update(
            @PathVariable String name, @Valid @RequestBody UpdateSettingRequest request, Authentication authentication) {
        String changedBy = SuperAdminSecurityConfig.currentSuperAdminUsername(authentication);
        return protectionSettingService.update(name, request.value(), changedBy);
    }

    @GetMapping("/{name}/history")
    public List<SettingHistoryEntryResponse> history(@PathVariable String name) {
        return protectionSettingService.history(name).stream().map(SettingHistoryEntryResponse::of).toList();
    }
}
