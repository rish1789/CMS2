package com.cms.identity.staff.api;

import com.cms.identity.staff.service.StaffPasswordResetService;


import com.cms.identity.account.config.SecurityConfig;
import com.cms.identity.staff.dto.ResetStaffPasswordResponse;
import com.cms.identity.staff.dto.SetStaffPasswordRequest;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * real-bug-fix 2026-09-17: same URL shape as StaffDeactivationController - a ClinicAdmin acting
 * on one staff member's Account at their own clinic. See StaffPasswordResetService's own Javadoc
 * for why a ClinicAdmin target is rejected (no override).
 */
@RestController
@RequestMapping("/api/v1/clinics/{clinicId}/staff/{accountId}")
public class StaffPasswordResetController {

    private final StaffPasswordResetService staffPasswordResetService;

    public StaffPasswordResetController(StaffPasswordResetService staffPasswordResetService) {
        this.staffPasswordResetService = staffPasswordResetService;
    }

    @PostMapping("/reset-password")
    public ResetStaffPasswordResponse resetPassword(
            @PathVariable UUID clinicId, @PathVariable UUID accountId, Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        return staffPasswordResetService.resetPassword(callerAccountId, clinicId, accountId);
    }

    @PostMapping("/set-password")
    public ResetStaffPasswordResponse setPassword(
            @PathVariable UUID clinicId,
            @PathVariable UUID accountId,
            @RequestBody SetStaffPasswordRequest request,
            Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        return staffPasswordResetService.setPassword(callerAccountId, clinicId, accountId, request.newPassword());
    }
}
