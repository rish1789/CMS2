package com.cms.identity.staff;

import com.cms.identity.account.SecurityConfig;
import com.cms.identity.staff.dto.DeactivateStaffRequest;
import com.cms.identity.staff.dto.DeactivateStaffResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clinics/{clinicId}/staff/{accountId}")
public class StaffDeactivationController {

    private final StaffDeactivationService staffDeactivationService;

    public StaffDeactivationController(StaffDeactivationService staffDeactivationService) {
        this.staffDeactivationService = staffDeactivationService;
    }

    @PostMapping("/deactivate")
    public ResponseEntity<DeactivateStaffResponse> deactivate(
            @PathVariable UUID clinicId,
            @PathVariable UUID accountId,
            @RequestBody(required = false) DeactivateStaffRequest request,
            Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        String reason = request == null ? null : request.reason();
        DeactivateStaffResponse response =
                staffDeactivationService.deactivate(callerAccountId, clinicId, accountId, reason);
        return ResponseEntity.ok(response);
    }
}
