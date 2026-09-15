package com.cms.patient.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cms.common.IndianMobileNumberValidator;
import com.cms.patient.api.dto.LoginRequest;
import com.cms.patient.api.dto.LoginResponse;
import com.cms.patient.api.dto.SignupRequest;
import com.cms.patient.api.dto.SignupResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit slice (no Spring context, no DB) for {@link PatientAccountService}'s business rules -
 * the pre-check-then-DB-constraint email-uniqueness pattern, validation ordering (mobile before
 * password), and the identical-exception-for-both-failure-modes login contract (FR-007).
 * Complements (does not replace) the Testcontainers-backed integration tests
 * (PatientSignupHappyPathTest, PatientSignupDuplicateEmailTest, PatientLoginTest, etc.), which
 * prove the real DB-level uniqueness constraint - not just this pre-check - actually holds.
 */
@ExtendWith(MockitoExtension.class)
class PatientAccountServiceTest {

    @Mock
    private PatientAccountRepository patientAccountRepository;

    @Mock
    private PasswordPolicyValidator passwordPolicyValidator;

    @Mock
    private IndianMobileNumberValidator mobileNumberValidator;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private PatientAccount account;

    private PatientAccountService service() {
        return new PatientAccountService(
                patientAccountRepository, passwordPolicyValidator, mobileNumberValidator, passwordEncoder, jwtService);
    }

    @Test
    void signupSavesAnEncodedPasswordAndReturnsTheNewAccount() {
        SignupRequest request = new SignupRequest("owner@example.com", "Str0ng!Pass", "9876543210");
        when(mobileNumberValidator.isValid("9876543210")).thenReturn(true);
        when(passwordPolicyValidator.validate("Str0ng!Pass")).thenReturn(List.of());
        when(patientAccountRepository.existsByEmail("owner@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Str0ng!Pass")).thenReturn("hashed-pw");
        when(patientAccountRepository.save(any(PatientAccount.class))).thenReturn(account);
        UUID accountId = UUID.randomUUID();
        when(account.getId()).thenReturn(accountId);
        when(account.getEmail()).thenReturn("owner@example.com");

        SignupResponse response = service().signup(request);

        assertThat(response).isEqualTo(new SignupResponse(accountId, "owner@example.com"));
    }

    @Test
    void signupRejectsAnAlreadyRegisteredEmailBeforeTouchingThePasswordEncoder() {
        SignupRequest request = new SignupRequest("owner@example.com", "Str0ng!Pass", "9876543210");
        when(mobileNumberValidator.isValid("9876543210")).thenReturn(true);
        when(passwordPolicyValidator.validate("Str0ng!Pass")).thenReturn(List.of());
        when(patientAccountRepository.existsByEmail("owner@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service().signup(request)).isInstanceOf(EmailAlreadyInUseException.class);
        verify(passwordEncoder, never()).encode(anyString());
        verify(patientAccountRepository, never()).save(any());
    }

    @Test
    void signupRejectsAPolicyViolatingPassword() {
        SignupRequest request = new SignupRequest("owner@example.com", "weak", "9876543210");
        when(mobileNumberValidator.isValid("9876543210")).thenReturn(true);
        when(passwordPolicyValidator.validate("weak")).thenReturn(List.of("MIN_LENGTH"));

        assertThatThrownBy(() -> service().signup(request)).isInstanceOf(InvalidPasswordException.class);
        verify(patientAccountRepository, never()).save(any());
    }

    @Test
    void signupRejectsAnInvalidMobileNumberBeforeCheckingThePassword() {
        SignupRequest request = new SignupRequest("owner@example.com", "Str0ng!Pass", "12345");
        when(mobileNumberValidator.isValid("12345")).thenReturn(false);

        assertThatThrownBy(() -> service().signup(request)).isInstanceOf(InvalidMobileNumberException.class);
        verify(passwordPolicyValidator, never()).validate(anyString());
    }

    @Test
    void loginWithCorrectCredentialsIssuesAToken() {
        LoginRequest request = new LoginRequest("owner@example.com", "Str0ng!Pass");
        when(patientAccountRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(account));
        when(account.getPasswordHash()).thenReturn("hashed-pw");
        when(passwordEncoder.matches("Str0ng!Pass", "hashed-pw")).thenReturn(true);
        UUID accountId = UUID.randomUUID();
        when(account.getId()).thenReturn(accountId);
        when(account.getEmail()).thenReturn("owner@example.com");
        when(jwtService.issueToken(accountId)).thenReturn("a.jwt.token");

        LoginResponse response = service().authenticate(request);

        assertThat(response).isEqualTo(new LoginResponse("a.jwt.token", accountId, "owner@example.com"));
    }

    @Test
    void loginWithWrongPasswordAndUnknownEmailShareTheSameException() {
        when(patientAccountRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(account));
        when(account.getPasswordHash()).thenReturn("hashed-pw");
        when(passwordEncoder.matches("wrong-password", "hashed-pw")).thenReturn(false);

        assertThatThrownBy(() -> service().authenticate(new LoginRequest("owner@example.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);

        when(patientAccountRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().authenticate(new LoginRequest("nobody@example.com", "irrelevant")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
