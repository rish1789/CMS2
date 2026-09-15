package com.cms.identity.account;

import com.cms.identity.account.dto.StaffLoginRequest;
import com.cms.identity.account.dto.StaffLoginResponse;
import com.cms.identity.admin.SuperAdminAuthenticationService;
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
 * (FR-002), and every failure mode (unknown identifier, wrong password, wrong Super Admin
 * password) shares one exception so no information leaks about which check failed (FR-004).
 *
 * <p>The Super Admin check goes through {@link SuperAdminAuthenticationService}, the one contract
 * {@code com.cms.identity.admin} exposes for this purpose - never its internal {@code
 * UserDetailsService}/JWT-signing beans directly (Constitution III, research.md R2).
 *
 * <p>Extracted out of {@link StaffAuthController} (previously inline in the route handler) so the
 * controller stays a thin HTTP adapter, mirroring the split {@code
 * com.cms.patient.account.PatientAccountService} already uses for the patient login equivalent.
 * Behavior is unchanged - see {@code StaffAuthServiceTest} for case-by-case proof.
 */
@Service
public class StaffAuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final StaffJwtService staffJwtService;
    private final SuperAdminAuthenticationService superAdminAuthenticationService;

    public StaffAuthService(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            StaffJwtService staffJwtService,
            SuperAdminAuthenticationService superAdminAuthenticationService) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.staffJwtService = staffJwtService;
        this.superAdminAuthenticationService = superAdminAuthenticationService;
    }

    public StaffLoginResponse login(StaffLoginRequest request) {
        Optional<String> superAdminToken =
                superAdminAuthenticationService.authenticate(request.identifier(), request.password());
        if (superAdminToken.isPresent()) {
            return new StaffLoginResponse(superAdminToken.get(), null, request.identifier(), "SUPER_ADMIN");
        }

        Account account = accountRepository
                .findByEmail(request.identifier())
                .or(() -> accountRepository.findByStaffCode(request.identifier()))
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        String token = staffJwtService.issueToken(account.getId());
        return new StaffLoginResponse(token, account.getId(), account.getEmail(), "STAFF");
    }
}
