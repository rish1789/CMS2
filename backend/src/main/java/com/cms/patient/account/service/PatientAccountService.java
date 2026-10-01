package com.cms.patient.account.service;

import com.cms.patient.account.config.JwtService;
import com.cms.patient.account.domain.PatientAccount;
import com.cms.patient.account.exception.EmailAlreadyInUseException;
import com.cms.patient.account.exception.InvalidCredentialsException;
import com.cms.patient.account.exception.InvalidMobileNumberException;
import com.cms.patient.account.exception.InvalidPasswordException;
import com.cms.patient.account.exception.MissingRequiredFieldException;
import com.cms.patient.account.exception.SignupFailedException;
import com.cms.patient.account.repository.PatientAccountRepository;


import com.cms.common.IndianMobileNumberValidator;
import com.cms.patient.api.dto.LoginRequest;
import com.cms.patient.api.dto.LoginResponse;
import com.cms.patient.api.dto.SignupRequest;
import com.cms.patient.api.dto.SignupResponse;
import com.cms.common.login.LoginAttemptGuard;
import com.cms.common.login.LoginRealm;
import java.util.List;
import java.util.Optional;
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
    private final LoginAttemptGuard loginAttemptGuard;
    private volatile String timingEqualiserHash;

    public PatientAccountService(
            PatientAccountRepository patientAccountRepository,
            PasswordPolicyValidator passwordPolicyValidator,
            IndianMobileNumberValidator mobileNumberValidator,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginAttemptGuard loginAttemptGuard) {
        this.patientAccountRepository = patientAccountRepository;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.mobileNumberValidator = mobileNumberValidator;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptGuard = loginAttemptGuard;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        validateSignup(request);

        try {
            PatientAccount account = new PatientAccount(
                    request.email(), passwordEncoder.encode(request.password()), request.mobile());
            // Flushed here, inside the try: a deferred INSERT would otherwise hit
            // uq_patient_account_email at commit, after this catch, and surface as an unmapped 500.
            account = patientAccountRepository.saveAndFlush(account);

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
     * 075-login-hardening (D-3C-2): an unregistered email and a wrong password get one generic
     * answer again - restoring this feature's original FR-007 no-leak behaviour, which an earlier
     * product decision had traded away - and both count towards the per-email lockout. An unknown
     * email still costs one hash comparison, so response time does not tell the two apart.
     */
    public LoginResponse authenticate(LoginRequest request) {
        loginAttemptGuard.requireNotLocked(LoginRealm.PATIENT, request.email());

        Optional<PatientAccount> found = patientAccountRepository.findByEmail(request.email());
        boolean passwordMatches = passwordEncoder.matches(
                request.password(), found.map(PatientAccount::getPasswordHash).orElseGet(this::timingEqualiserHash));
        if (found.isEmpty() || !passwordMatches) {
            loginAttemptGuard.recordFailure(LoginRealm.PATIENT, request.email());
            throw new InvalidCredentialsException("Incorrect email or password.");
        }
        PatientAccount account = found.get();
        loginAttemptGuard.recordSuccess(LoginRealm.PATIENT, request.email());

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

    /** Built on first use (not at startup) - one bcrypt hash an unknown identifier is compared against. */
    private String timingEqualiserHash() {
        String hash = timingEqualiserHash;
        if (hash == null) {
            hash = passwordEncoder.encode("timing-equaliser-not-a-real-password");
            timingEqualiserHash = hash;
        }
        return hash;
    }
}
