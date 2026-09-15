package com.cms.identity.account;

import com.cms.identity.account.dto.ClinicMembershipListResponse;
import com.cms.identity.account.dto.ClinicMembershipResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 041-staff-console-pickers FR-001: lists the clinics the caller currently has an active
 * role at, replacing the "type a Clinic ID" entry point. Not clinic-scoped - there is no
 * separate clinic to be forbidden from, an Account with zero active roles simply gets an
 * empty list (contracts/staff-console-pickers.md).
 */
@RestController
@RequestMapping("/api/v1/clinics")
public class StaffClinicController {

    private final RoleAssignmentRepository roleAssignmentRepository;

    public StaffClinicController(RoleAssignmentRepository roleAssignmentRepository) {
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    private static final int DEFAULT_PAGE_SIZE = 20;

    @GetMapping("/mine")
    public ClinicMembershipListResponse mine(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            Authentication authentication) {
        var accountId = SecurityConfig.currentAccountId(authentication);
        Page<RoleAssignment> rolePage =
                roleAssignmentRepository.findByAccount_IdAndActiveTrue(accountId, PageRequest.of(page, size));
        var clinics = rolePage.getContent().stream().map(ClinicMembershipResponse::from).toList();
        return new ClinicMembershipListResponse(clinics, page, size, rolePage.getTotalElements());
    }
}
