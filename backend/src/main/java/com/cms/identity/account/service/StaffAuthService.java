package com.cms.identity.account.service;

import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.exception.StaffClinicNotActiveException;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import java.util.List;
import java.util.UUID;
import com.cms.identity.account.api.StaffAuthController;
import com.cms.identity.account.config.StaffJwtService;
import com.cms.identity.account.domain.Account;
import com.cms.identity.account.exception.AccountNotFoundException;
import com.cms.identity.account.exception.IncorrectPasswordException;
import com.cms.identity.account.repository.AccountRepository;


import com.cms.identity.account.dto.StaffLoginRequest;
import com.cms.identity.account.dto.StaffLoginResponse;
import com.cms.identity.admin.service.SuperAdminAuthenticationService;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Business logic for the "Clinic Portal" login (040-super-admin-rbac-login): email-or-staff-code
 * + password for any staff Account (ClinicAdmin/Doctor/Operations), resolving the configured
 * Super Admin credential first (research.md R2). The email-only path was pulled forward by
 * 004-staff-onboarding-direct-hire as the minimal, real authentication that feature needed to be
 * usable end-to-end; 006-staff-login-dual-identifier added the staff-code alternate identifier to
 * this same mechanism - both resolve to the same Account and produce an identical response shape
 * (FR-002). Failure modes deliberately distinguish {@link AccountNotFoundException} (no matching
 * identifier - staff or Super Admin) from {@link IncorrectPasswordException} (identifier resolved,
 * password didn't match): a product decision accepting the resulting user-enumeration tradeoff in
 * exchange for a more specific login error (supersedes this feature's original FR-004 no-leak
 * behavior).
 *
 * <p>The Super Admin check goes through {@link SuperAdminAuthenticationService}, the one contract
 * {@code com.cms.identity.admin} exposes for this purpose - never its internal {@code
 * UserDetailsService}/JWT-signing beans directly (Constitution III, research.md R2).
 *
 * <p>Extracted out of {@link StaffAuthController} (previously inline in the route handler) so the
 * controller stays a thin HTTP adapter, mirroring the split {@code
 * com.cms.patient.account.service.PatientAccountService} already uses for the patient login equivalent.
 * Behavior is unchanged - see {@code StaffAuthServiceTest} for case-by-case proof.
 */
@Service
public class StaffAuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final StaffJwtService staffJwtService;
    private final SuperAdminAuthenticationService superAdminAuthenticationService;
    private final RoleAssignmentRepository roleAssignmentRepository;

    public StaffAuthService(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            StaffJwtService staffJwtService,
            SuperAdminAuthenticationService superAdminAuthenticationService,
            RoleAssignmentRepository roleAssignmentRepository) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.staffJwtService = staffJwtService;
        this.superAdminAuthenticationService = superAdminAuthenticationService;
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    public StaffLoginResponse login(StaffLoginRequest request) {
        Optional<String> superAdminToken =
                superAdminAuthenticationService.authenticate(request.identifier(), request.password());
        if (superAdminToken.isPresent()) {
            return new StaffLoginResponse(superAdminToken.get(), null, request.identifier(), "SUPER_ADMIN");
        }

        // The identifier matched the configured Super Admin username above, but the password
        // didn't - report it as a wrong password, not "account not found", rather than falling
        // through to a staff lookup that can never match a Super Admin username anyway.
        if (superAdminAuthenticationService.identifierMatches(request.identifier())) {
            throw new IncorrectPasswordException();
        }

        Account account = accountRepository
                .findByEmail(request.identifier())
                .or(() -> accountRepository.findByStaffCode(request.identifier()))
                .orElseThrow(AccountNotFoundException::new);
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new IncorrectPasswordException();
        }
        requireAnActiveClinic(account.getId());
        String token = staffJwtService.issueToken(account.getId());
        return new StaffLoginResponse(token, account.getId(), account.getEmail(), "STAFF");
    }

    /**
     * 062-rejected-clinic-gating (FR-007): at a rejected clinic only the ClinicAdmin keeps access.
     * Refuses sign-in only when the account has roles and every one of them is Doctor/Operations at
     * a rejected clinic - one usable role anywhere is enough, and an account with no roles at all
     * signs in exactly as before.
     */
    private void requireAnActiveClinic(UUID accountId) {
        List<RoleAssignment> roles = roleAssignmentRepository.findByAccount_IdAndActiveTrue(accountId);
        boolean allBlocked = !roles.isEmpty()
                && roles.stream()
                        .allMatch(role -> role.getClinic().isRejected()
                                && role.getRole() != RoleAssignment.Role.ClinicAdmin);
        if (allBlocked) {
            throw new StaffClinicNotActiveException();
        }
    }
}
