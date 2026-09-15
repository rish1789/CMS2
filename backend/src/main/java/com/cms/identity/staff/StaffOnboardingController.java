package com.cms.identity.staff;

import com.cms.identity.account.SecurityConfig;
import com.cms.identity.staff.dto.OnboardStaffRequest;
import com.cms.identity.staff.dto.OnboardStaffResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clinics/{clinicId}/staff")
public class StaffOnboardingController {

    private final StaffOnboardingService staffOnboardingService;

    public StaffOnboardingController(StaffOnboardingService staffOnboardingService) {
        this.staffOnboardingService = staffOnboardingService;
    }

    @PostMapping
    public ResponseEntity<OnboardStaffResponse> onboard(
            @PathVariable UUID clinicId, @Valid @RequestBody OnboardStaffRequest request, Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        OnboardStaffResponse response = staffOnboardingService.onboard(callerAccountId, clinicId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
