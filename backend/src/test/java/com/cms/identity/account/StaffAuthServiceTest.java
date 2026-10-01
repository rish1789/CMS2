package com.cms.identity.account;

import com.cms.identity.account.api.StaffAuthController;
import com.cms.identity.account.config.StaffJwtService;
import com.cms.identity.account.domain.Account;
import com.cms.common.login.LoginAttemptGuard;
import com.cms.common.login.LoginRealm;
import com.cms.identity.account.exception.InvalidCredentialsException;
import com.cms.identity.account.exception.NoActiveClinicAccessException;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.exception.StaffClinicNotActiveException;
import com.cms.identity.account.repository.AccountRepository;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.clinic.Clinic;
import java.util.List;
import com.cms.identity.account.service.StaffAuthService;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cms.identity.account.dto.StaffLoginRequest;
import com.cms.identity.account.dto.StaffLoginResponse;
import com.cms.identity.admin.service.SuperAdminAuthenticationService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit slice (no Spring context, no DB) for the login resolution logic extracted from {@link
 * StaffAuthController} into {@link StaffAuthService}. Proves the extraction preserved every
 * branch of the original inline implementation - complements (does not replace) the
 * Testcontainers-backed {@code StaffLoginTest}, which exercises the same endpoint end-to-end
 * through the real Spring Security filter chain.
 */
@ExtendWith(MockitoExtension.class)
class StaffAuthServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private StaffJwtService staffJwtService;

    @Mock
    private SuperAdminAuthenticationService superAdminAuthenticationService;

    @Mock
    private Account account;

    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;

    // 075-login-hardening: the lockout guard (its own behaviour is LoginAttemptStateTest's and
    // LoginHardeningTest's); here only that failures and successes are reported to it.
    @Mock
    private LoginAttemptGuard loginAttemptGuard;

    private StaffAuthService staffAuthService() {
        return new StaffAuthService(
                accountRepository, passwordEncoder, staffJwtService, superAdminAuthenticationService,
                roleAssignmentRepository, loginAttemptGuard);
    }

    @Test
    void superAdminCredentialsShortCircuitBeforeAnyAccountLookup() {
        when(superAdminAuthenticationService.authenticate("superadmin", "Str0ng!AdminPass"))
                .thenReturn(Optional.of("super-admin-jwt"));

        StaffLoginResponse response =
                staffAuthService().login(new StaffLoginRequest("superadmin", "Str0ng!AdminPass"));

        assertThat(response)
                .isEqualTo(new StaffLoginResponse("super-admin-jwt", null, "superadmin", "SUPER_ADMIN"));
        verifyNoInteractions(accountRepository, passwordEncoder, staffJwtService);
    }

    @Test
    void correctCredentialsResolveByEmailAndIssueStaffToken() {
        UUID accountId = UUID.randomUUID();
        when(superAdminAuthenticationService.authenticate(anyString(), anyString())).thenReturn(Optional.empty());
        when(accountRepository.findByEmail("staff@example.com")).thenReturn(Optional.of(account));
        when(account.getPasswordHash()).thenReturn("hashed-pw");
        when(passwordEncoder.matches("Str0ng!Pass", "hashed-pw")).thenReturn(true);
        when(account.getId()).thenReturn(accountId);
        when(account.getEmail()).thenReturn("staff@example.com");
        when(staffJwtService.issueToken(accountId)).thenReturn("staff-jwt");
        when(roleAssignmentRepository.existsByAccount_IdAndActiveTrue(accountId)).thenReturn(true);

        StaffLoginResponse response =
                staffAuthService().login(new StaffLoginRequest("staff@example.com", "Str0ng!Pass"));

        assertThat(response)
                .isEqualTo(new StaffLoginResponse("staff-jwt", accountId, "staff@example.com", "STAFF"));
    }

    @Test
    void fallsBackToStaffCodeWhenEmailLookupMisses() {
        UUID accountId = UUID.randomUUID();
        when(superAdminAuthenticationService.authenticate(anyString(), anyString())).thenReturn(Optional.empty());
        when(accountRepository.findByEmail("OP-0042")).thenReturn(Optional.empty());
        when(accountRepository.findByStaffCode("OP-0042")).thenReturn(Optional.of(account));
        when(account.getPasswordHash()).thenReturn("hashed-pw");
        when(passwordEncoder.matches("Str0ng!Pass", "hashed-pw")).thenReturn(true);
        when(account.getId()).thenReturn(accountId);
        when(account.getEmail()).thenReturn("ops@example.com");
        when(staffJwtService.issueToken(accountId)).thenReturn("staff-jwt");
        when(roleAssignmentRepository.existsByAccount_IdAndActiveTrue(accountId)).thenReturn(true);

        StaffLoginResponse response = staffAuthService().login(new StaffLoginRequest("OP-0042", "Str0ng!Pass"));

        assertThat(response.role()).isEqualTo("STAFF");
        assertThat(response.email()).isEqualTo("ops@example.com");
    }

    @Test
    void wrongPasswordForAKnownIdentifierFailsGenericallyWithoutIssuingAToken() {
        when(superAdminAuthenticationService.authenticate(anyString(), anyString())).thenReturn(Optional.empty());
        when(superAdminAuthenticationService.identifierMatches("staff@example.com")).thenReturn(false);
        when(accountRepository.findByEmail("staff@example.com")).thenReturn(Optional.of(account));
        when(account.getPasswordHash()).thenReturn("hashed-pw");
        when(passwordEncoder.matches("wrong-password", "hashed-pw")).thenReturn(false);

        assertThatThrownBy(
                        () -> staffAuthService().login(new StaffLoginRequest("staff@example.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(staffJwtService, never()).issueToken(any());
        verify(loginAttemptGuard).recordFailure(LoginRealm.STAFF, "staff@example.com");
    }

    @Test
    /** 075-login-hardening (D-3C-2): the same failure as a wrong password - and the same hash work, so timing can't tell them apart. */
    void unknownIdentifierFailsExactlyLikeAWrongPassword() {
        when(superAdminAuthenticationService.authenticate(anyString(), anyString())).thenReturn(Optional.empty());
        when(superAdminAuthenticationService.identifierMatches("nobody@example.com")).thenReturn(false);
        when(accountRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        when(accountRepository.findByStaffCode("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                        staffAuthService().login(new StaffLoginRequest("nobody@example.com", "irrelevant")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(passwordEncoder).matches(org.mockito.ArgumentMatchers.eq("irrelevant"), any());
        verifyNoInteractions(staffJwtService);
        verify(loginAttemptGuard).recordFailure(LoginRealm.STAFF, "nobody@example.com");
    }

    @Test
    void wrongPasswordForTheSuperAdminIdentifierFailsGenericallyAndCounts() {
        when(superAdminAuthenticationService.authenticate("superadmin", "wrong-password"))
                .thenReturn(Optional.empty());
        when(superAdminAuthenticationService.identifierMatches("superadmin")).thenReturn(true);

        assertThatThrownBy(() -> staffAuthService().login(new StaffLoginRequest("superadmin", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(accountRepository, staffJwtService);
        verify(loginAttemptGuard).recordFailure(LoginRealm.STAFF, "superadmin");
    }

    // ---- 062-rejected-clinic-gating (FR-007, tasks.md T022): only a ClinicAdmin keeps access to a rejected clinic ----

    private Clinic clinic(boolean rejected) {
        Clinic clinic = new Clinic("Clinic", "1 Main St", null, null);
        if (rejected) {
            clinic.reject(Clinic.RejectionReason.DUPLICATE_REGISTRATION, null, "super-admin");
        }
        return clinic;
    }

    private RoleAssignment role(RoleAssignment.Role role, boolean clinicRejected) {
        return new RoleAssignment(account, clinic(clinicRejected), role);
    }

    private void passwordMatchesFor(UUID accountId) {
        when(superAdminAuthenticationService.authenticate(anyString(), anyString())).thenReturn(Optional.empty());
        when(accountRepository.findByEmail("staff@example.com")).thenReturn(Optional.of(account));
        when(account.getPasswordHash()).thenReturn("hashed-pw");
        when(passwordEncoder.matches("Str0ng!Pass", "hashed-pw")).thenReturn(true);
        when(account.getId()).thenReturn(accountId);
        org.mockito.Mockito.lenient().when(account.getEmail()).thenReturn("staff@example.com");
        org.mockito.Mockito.lenient().when(staffJwtService.issueToken(accountId)).thenReturn("staff-jwt");
    }

    @Test
    void aDoctorWhoseOnlyClinicIsRejectedIsRefusedAtSignIn() {
        UUID accountId = UUID.randomUUID();
        passwordMatchesFor(accountId);
        when(roleAssignmentRepository.findByAccount_IdAndActiveTrue(accountId))
                .thenReturn(List.of(role(RoleAssignment.Role.Doctor, true)));

        assertThatThrownBy(() -> staffAuthService().login(new StaffLoginRequest("staff@example.com", "Str0ng!Pass")))
                .isInstanceOf(StaffClinicNotActiveException.class);
        verify(staffJwtService, never()).issueToken(any());
    }

    @Test
    void theClinicAdminOfARejectedClinicStillSignsIn() {
        UUID accountId = UUID.randomUUID();
        passwordMatchesFor(accountId);
        when(roleAssignmentRepository.findByAccount_IdAndActiveTrue(accountId))
                .thenReturn(List.of(role(RoleAssignment.Role.ClinicAdmin, true)));
        when(roleAssignmentRepository.existsByAccount_IdAndActiveTrue(accountId)).thenReturn(true);

        assertThat(staffAuthService().login(new StaffLoginRequest("staff@example.com", "Str0ng!Pass")).token())
                .isEqualTo("staff-jwt");
    }

    @Test
    void aDoctorWithAnotherUsableClinicStillSignsIn() {
        UUID accountId = UUID.randomUUID();
        passwordMatchesFor(accountId);
        when(roleAssignmentRepository.findByAccount_IdAndActiveTrue(accountId))
                .thenReturn(List.of(role(RoleAssignment.Role.Doctor, true), role(RoleAssignment.Role.Doctor, false)));
        when(roleAssignmentRepository.existsByAccount_IdAndActiveTrue(accountId)).thenReturn(true);

        assertThat(staffAuthService().login(new StaffLoginRequest("staff@example.com", "Str0ng!Pass")).token())
                .isEqualTo("staff-jwt");
    }

    /**
     * 075-login-hardening (D-3C-1) supersedes 062's "an account with no roles signs in exactly as
     * before": with no active role at any clinic, the right password now gets NO_ACTIVE_CLINIC_ACCESS.
     */
    @Test
    void anAccountWithNoActiveRoleIsRefusedAfterTheRightPassword() {
        UUID accountId = UUID.randomUUID();
        passwordMatchesFor(accountId);
        when(roleAssignmentRepository.findByAccount_IdAndActiveTrue(accountId)).thenReturn(List.of());
        when(roleAssignmentRepository.existsByAccount_IdAndActiveTrue(accountId)).thenReturn(false);

        assertThatThrownBy(() -> staffAuthService().login(new StaffLoginRequest("staff@example.com", "Str0ng!Pass")))
                .isInstanceOf(NoActiveClinicAccessException.class);
        verify(staffJwtService, never()).issueToken(any());
        verify(loginAttemptGuard).recordSuccess(LoginRealm.STAFF, "staff@example.com");
    }
}
