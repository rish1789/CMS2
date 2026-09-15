package com.cms.identity.clinic;

import com.cms.identity.account.Account;
import com.cms.identity.account.AccountRepository;
import com.cms.identity.account.PasswordPolicyValidator;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.identity.account.StaffCodeGenerator;
import com.cms.identity.api.dto.RegisterClinicRequest;
import com.cms.identity.api.dto.RegisterClinicResponse;
import com.cms.common.IndianMobileNumberValidator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements FR-001..FR-013: validates the submission, then atomically creates a Clinic
 * (verified=false), an Account (hashed password, generated staff code), and a
 * ClinicAdmin RoleAssignment in one transaction, rolling back wholesale on any failure
 * (FR-002, FR-003).
 */
@Service
public class ClinicRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(ClinicRegistrationService.class);
    private static final String CLINIC_ADMIN_STAFF_CODE_PREFIX = "CA";

    private final ClinicRepository clinicRepository;
    private final AccountRepository accountRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final IndianMobileNumberValidator mobileNumberValidator;
    private final StaffCodeGenerator staffCodeGenerator;
    private final PasswordEncoder passwordEncoder;

    public ClinicRegistrationService(
            ClinicRepository clinicRepository,
            AccountRepository accountRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            PasswordPolicyValidator passwordPolicyValidator,
            IndianMobileNumberValidator mobileNumberValidator,
            StaffCodeGenerator staffCodeGenerator,
            PasswordEncoder passwordEncoder) {
        this.clinicRepository = clinicRepository;
        this.accountRepository = accountRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.mobileNumberValidator = mobileNumberValidator;
        this.staffCodeGenerator = staffCodeGenerator;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterClinicResponse register(RegisterClinicRequest request) {
        validate(request);

        try {
            Clinic clinic = new Clinic(
                    request.clinic().name(),
                    request.clinic().address(),
                    request.clinic().city(),
                    request.clinic().contactEmail(),
                    request.clinic().contactMobile());
            clinic = clinicRepository.save(clinic);

            String staffCode = staffCodeGenerator.generate(CLINIC_ADMIN_STAFF_CODE_PREFIX);
            Account account = new Account(
                    request.admin().name(),
                    request.admin().email(),
                    passwordEncoder.encode(request.admin().password()),
                    staffCode,
                    request.admin().mobile());
            account = accountRepository.save(account);

            RoleAssignment roleAssignment = new RoleAssignment(account, clinic, RoleAssignment.Role.ClinicAdmin);
            roleAssignmentRepository.save(roleAssignment);

            log.info("Clinic registration succeeded: clinicId={}", clinic.getId());

            return new RegisterClinicResponse(
                    clinic.getId(),
                    clinic.getName(),
                    clinic.isVerified(),
                    new RegisterClinicResponse.AdminInfo(account.getId(), account.getEmail(), account.getStaffCode()));
        } catch (DataAccessException e) {
            // Covers the DB-level unique-constraint race on email/staff_code (Constitution
            // Principle IV) surfacing here despite the pre-check in validate(), plus any
            // other persistence failure - either way the @Transactional annotation above
            // rolls back every write made in this method, so no orphaned Clinic is left
            // (FR-003).
            log.warn("Clinic registration failed: {}", e.getClass().getSimpleName());
            if (isUniqueConstraintViolation(e, "uq_account_email")) {
                throw new EmailAlreadyInUseException();
            }
            throw new RegistrationFailedException(e);
        }
    }

    private void validate(RegisterClinicRequest request) {
        requireNonBlank(request.clinic().name(), "clinic.name");
        requireNonBlank(request.clinic().address(), "clinic.address");
        requireNonBlank(request.admin().name(), "admin.name");
        requireNonBlank(request.admin().email(), "admin.email");
        requireNonBlank(request.admin().password(), "admin.password");

        if (!mobileNumberValidator.isValid(request.clinic().contactMobile())) {
            throw new InvalidMobileNumberException("clinic.contactMobile");
        }
        if (!mobileNumberValidator.isValid(request.admin().mobile())) {
            throw new InvalidMobileNumberException("admin.mobile");
        }

        List<String> failedPasswordRules = passwordPolicyValidator.validate(request.admin().password());
        if (!failedPasswordRules.isEmpty()) {
            throw new InvalidPasswordException(failedPasswordRules);
        }

        // Fast, friendly pre-check - NOT the source of correctness. The DB-level unique
        // constraint (caught above) is what actually closes the concurrent-registration
        // race per Constitution Principle IV; this only avoids an unnecessary round trip
        // for the common, non-racing case.
        if (accountRepository.existsByEmail(request.admin().email())) {
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
