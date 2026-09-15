package com.cms.patient.account;

import com.cms.common.IndianMobileNumberValidator;
import com.cms.patient.api.dto.LoginRequest;
import com.cms.patient.api.dto.LoginResponse;
import com.cms.patient.api.dto.SignupRequest;
import com.cms.patient.api.dto.SignupResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements FR-001..FR-009: self-service signup and login for the global Patient
 * Account identity - entirely separate persistence and auth logic from staff Account
 * (001), per FR-004/FR-008.
 */
@Service
public class PatientAccountService {

    private static final Logger log = LoggerFactory.getLogger(PatientAccountService.class);

    private final PatientAccountRepository patientAccountRepository;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final IndianMobileNumberValidator mobileNumberValidator;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public PatientAccountService(
            PatientAccountRepository patientAccountRepository,
            PasswordPolicyValidator passwordPolicyValidator,
            IndianMobileNumberValidator mobileNumberValidator,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.patientAccountRepository = patientAccountRepository;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.mobileNumberValidator = mobileNumberValidator;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        validateSignup(request);

        try {
            PatientAccount account = new PatientAccount(
                    request.email(), passwordEncoder.encode(request.password()), request.mobile());
            account = patientAccountRepository.save(account);

            log.info("Patient Account signup succeeded: patientAccountId={}", account.getId());

            return new SignupResponse(account.getId(), account.getEmail());
        } catch (DataAccessException e) {
            // Covers the DB-level unique-constraint race on email (Constitution Principle
            // IV) surfacing here despite the pre-check in validateSignup(), plus any other
            // persistence failure - either way @Transactional rolls back the write, so no
            // orphaned/partial row is left (mirrors 001's FR-003 pattern).
            log.warn("Patient Account signup failed: {}", e.getClass().getSimpleName());
            if (isUniqueConstraintViolation(e, "uq_patient_account_email")) {
                throw new EmailAlreadyInUseException();
            }
            throw new SignupFailedException(e);
        }
    }

    /**
     * FR-007: rejects an incorrect password without revealing whether the email itself
     * is registered - both cases throw the identical {@link InvalidCredentialsException}.
     */
    public LoginResponse authenticate(LoginRequest request) {
        PatientAccount account = patientAccountRepository
                .findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.issueToken(account.getId());
        log.info("Patient Account login succeeded: patientAccountId={}", account.getId());
        return new LoginResponse(token, account.getId(), account.getEmail());
    }

    private void validateSignup(SignupRequest request) {
        requireNonBlank(request.email(), "email");
        requireNonBlank(request.password(), "password");

        if (!mobileNumberValidator.isValid(request.mobile())) {
            throw new InvalidMobileNumberException();
        }

        List<String> failedPasswordRules = passwordPolicyValidator.validate(request.password());
        if (!failedPasswordRules.isEmpty()) {
            throw new InvalidPasswordException(failedPasswordRules);
        }

        // Fast, friendly pre-check - NOT the source of correctness. The DB-level unique
        // constraint (caught in signup()) is what actually closes the concurrent-signup
        // race per Constitution Principle IV; this only avoids an unnecessary round trip
        // for the common, non-racing case. Deliberately checks patientAccountRepository
        // only - never the staff AccountRepository (FR-004: independent uniqueness).
        if (patientAccountRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyInUseException();
        }
    }

    private void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new MissingRequiredFieldException(field);
        }
    }

    private boolean isUniqueConstraintViolation(DataAccessException e, String constraintName) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(constraintName);
    }
}
