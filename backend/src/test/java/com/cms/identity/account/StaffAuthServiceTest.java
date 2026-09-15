package com.cms.identity.account;

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
import com.cms.identity.admin.SuperAdminAuthenticationService;
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

    private StaffAuthService staffAuthService() {
        return new StaffAuthService(
                accountRepository, passwordEncoder, staffJwtService, superAdminAuthenticationService);
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

        StaffLoginResponse response = staffAuthService().login(new StaffLoginRequest("OP-0042", "Str0ng!Pass"));

        assertThat(response.role()).isEqualTo("STAFF");
        assertThat(response.email()).isEqualTo("ops@example.com");
    }

    @Test
    void wrongPasswordRejectedWithoutIssuingAToken() {
        when(superAdminAuthenticationService.authenticate(anyString(), anyString())).thenReturn(Optional.empty());
        when(accountRepository.findByEmail("staff@example.com")).thenReturn(Optional.of(account));
        when(account.getPasswordHash()).thenReturn("hashed-pw");
        when(passwordEncoder.matches("wrong-password", "hashed-pw")).thenReturn(false);

        assertThatThrownBy(
                        () -> staffAuthService().login(new StaffLoginRequest("staff@example.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(staffJwtService, never()).issueToken(any());
    }

    @Test
    void unknownIdentifierRejectedWithSameExceptionAsWrongPassword() {
        when(superAdminAuthenticationService.authenticate(anyString(), anyString())).thenReturn(Optional.empty());
        when(accountRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        when(accountRepository.findByStaffCode("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                        staffAuthService().login(new StaffLoginRequest("nobody@example.com", "irrelevant")))
                .isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(passwordEncoder, staffJwtService);
    }
}
