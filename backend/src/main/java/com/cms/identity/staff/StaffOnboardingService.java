package com.cms.identity.staff;

import com.cms.common.IndianMobileNumberValidator;
import com.cms.identity.account.Account;
import com.cms.identity.account.AccountRepository;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.identity.account.StaffCodeGenerator;
import com.cms.identity.account.TemporaryPasswordGenerator;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.identity.clinic.EmailAlreadyInUseException;
import com.cms.identity.clinic.InvalidMobileNumberException;
import com.cms.identity.clinic.MissingRequiredFieldException;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.identity.staff.dto.OnboardStaffRequest;
import com.cms.identity.staff.dto.OnboardStaffResponse;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements FR-001..FR-010 (004) plus 007's onboarding-time dedup (FR-001..FR-002c): an
 * authenticated ClinicAdmin onboards a Doctor or Operations staff member for their own
 * clinic. Account + RoleAssignment (+ DoctorProfile for the Doctor path) are created
 * atomically, active immediately, with a generated staff code and temporary password -
 * unless the Doctor's license number already matches an existing global Doctor Profile
 * with the same specialization, in which case the existing Account/Profile are reused
 * (007 data-model.md) with no new credentials issued.
 */
@Service
public class StaffOnboardingService {

    private static final Logger log = LoggerFactory.getLogger(StaffOnboardingService.class);

    private final ClinicRepository clinicRepository;
    private final AccountRepository accountRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final IndianMobileNumberValidator mobileNumberValidator;
    private final StaffCodeGenerator staffCodeGenerator;
    private final TemporaryPasswordGenerator temporaryPasswordGenerator;
    private final PasswordEncoder passwordEncoder;

    public StaffOnboardingService(
            ClinicRepository clinicRepository,
            AccountRepository accountRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            DoctorProfileRepository doctorProfileRepository,
            IndianMobileNumberValidator mobileNumberValidator,
            StaffCodeGenerator staffCodeGenerator,
            TemporaryPasswordGenerator temporaryPasswordGenerator,
            PasswordEncoder passwordEncoder) {
        this.clinicRepository = clinicRepository;
        this.accountRepository = accountRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.doctorProfileRepository = doctorProfileRepository;
        this.mobileNumberValidator = mobileNumberValidator;
        this.staffCodeGenerator = staffCodeGenerator;
        this.temporaryPasswordGenerator = temporaryPasswordGenerator;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public OnboardStaffResponse onboard(UUID callerAccountId, UUID clinicId, OnboardStaffRequest request) {
        // FR-002: only an active ClinicAdmin for THIS specific clinic may onboard staff
        // here - a per-path-variable check, not expressible as a static security-config
        // rule (SecurityConfig's javadoc explains the split).
        if (!roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                callerAccountId, clinicId, RoleAssignment.Role.ClinicAdmin)) {
            throw new ForbiddenException();
        }

        RoleAssignment.Role role = parseRole(request.role());
        mustBeNonBlank(request.name(), "name");
        if (!mobileNumberValidator.isValid(request.mobile())) {
            throw new InvalidMobileNumberException("mobile");
        }
        if (role == RoleAssignment.Role.Doctor) {
            requireDoctorFields(request.doctor());
        }

        Clinic clinic = clinicRepository
                .findById(clinicId)
                .orElseThrow(() -> new NoSuchElementException("No clinic with id " + clinicId));

        // 007 FR-002/FR-002a: a Doctor submission whose license number matches an existing
        // global Doctor Profile reuses that Account/Profile instead of creating a
        // duplicate - checked before the email-uniqueness pre-check below, since the reuse
        // branch never touches Account creation/email uniqueness at all.
        if (role == RoleAssignment.Role.Doctor) {
            var existingProfile = doctorProfileRepository.findByLicenseNumber(
                    request.doctor().licenseNumber());
            if (existingProfile.isPresent()) {
                return reuseExistingDoctor(existingProfile.get(), request, clinic);
            }
        }

        // Fast, friendly pre-check - not the source of correctness; the DB-level unique
        // constraint (caught below) is what actually closes the race (Constitution
        // Principle IV), same pattern as 001/002's own onboarding-adjacent flows.
        if (accountRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyInUseException();
        }

        try {
            String rolePrefix = role == RoleAssignment.Role.Doctor ? "DR" : "OP";
            String staffCode = staffCodeGenerator.generate(rolePrefix);
            String temporaryPassword = temporaryPasswordGenerator.generate();

            Account account = new Account(
                    request.name(), request.email(), passwordEncoder.encode(temporaryPassword), staffCode, request.mobile());
            account = accountRepository.save(account);

            roleAssignmentRepository.save(new RoleAssignment(account, clinic, role));

            UUID doctorProfileId = null;
            if (role == RoleAssignment.Role.Doctor) {
                DoctorProfile doctorProfile = new DoctorProfile(
                        account,
                        request.doctor().specialization(),
                        request.doctor().licenseNumber(),
                        request.doctor().experienceYears());
                doctorProfile = doctorProfileRepository.save(doctorProfile);
                doctorProfileId = doctorProfile.getId();
            }

            log.info("Staff onboarded: accountId={}, clinicId={}, role={}", account.getId(), clinicId, role);

            return new OnboardStaffResponse(
                    account.getId(), account.getEmail(), staffCode, temporaryPassword, role.name(), doctorProfileId, false);
        } catch (DataAccessException e) {
            // @Transactional rolls back every write above on any exception, including this
            // one - no orphaned Account/RoleAssignment/DoctorProfile is left (FR-009).
            log.warn("Staff onboarding failed: {}", e.getClass().getSimpleName());
            if (isUniqueConstraintViolation(e, "uq_account_email")) {
                throw new EmailAlreadyInUseException();
            }
            // 007: a uq_doctor_profile_license_number violation here means this submission
            // lost a concurrent race against another submission for the same new license
            // number (Constitution Principle IV) - not a validation error (the pre-check
            // above found nothing at read time); the caller should retry, at which point
            // it will hit the reuse branch above instead. Falls through to the same
            // generic failure response as any other unexpected write failure.
            throw new OnboardingFailedException(e);
        }
    }

    /**
     * 007 FR-002/FR-002b/FR-002c: reuses an existing Account/Doctor Profile for a new
     * clinic - only a new RoleAssignment is written, licenseVerified/visible are never
     * touched, and no new credentials are generated.
     */
    private OnboardStaffResponse reuseExistingDoctor(
            DoctorProfile existingProfile, OnboardStaffRequest request, Clinic clinic) {
        String submittedSpecialization = request.doctor().specialization().trim();
        String existingSpecialization = existingProfile.getSpecialization().trim();
        if (!submittedSpecialization.equalsIgnoreCase(existingSpecialization)) {
            throw new SpecializationMismatchException();
        }

        Account existingAccount = existingProfile.getAccount();
        // Idempotent: this doctor may already be staffed at this exact clinic (e.g. a
        // duplicate onboarding submission) - a second RoleAssignment row would violate
        // uq_role_assignment_account_clinic_role_active and, worse, break deactivation
        // (which expects at most one row per account/clinic). Skip the write if it's
        // already true rather than erroring.
        boolean alreadyStaffedHere = roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                existingAccount.getId(), clinic.getId(), RoleAssignment.Role.Doctor);
        if (!alreadyStaffedHere) {
            roleAssignmentRepository.save(new RoleAssignment(existingAccount, clinic, RoleAssignment.Role.Doctor));
        }

        log.info(
                "Doctor onboarding reused existing profile: accountId={}, clinicId={}, alreadyStaffedHere={}",
                existingAccount.getId(),
                clinic.getId(),
                alreadyStaffedHere);

        return new OnboardStaffResponse(
                existingAccount.getId(),
                existingAccount.getEmail(),
                existingAccount.getStaffCode(),
                null,
                RoleAssignment.Role.Doctor.name(),
                existingProfile.getId(),
                true);
    }

    private RoleAssignment.Role parseRole(String role) {
        if ("Doctor".equals(role)) {
            return RoleAssignment.Role.Doctor;
        }
        if ("Operations".equals(role)) {
            return RoleAssignment.Role.Operations;
        }
        // Covers ClinicAdmin, SuperAdmin, and any garbage value - always 400, never
        // silently accepted, per FR-003's server-side enforcement.
        throw new InvalidRoleException(role);
    }

    private void requireDoctorFields(OnboardStaffRequest.DoctorDto doctor) {
        if (doctor == null) {
            throw new MissingRequiredFieldException("doctor");
        }
        mustBeNonBlank(doctor.specialization(), "doctor.specialization");
        mustBeNonBlank(doctor.licenseNumber(), "doctor.licenseNumber");
        if (doctor.experienceYears() == null) {
            throw new MissingRequiredFieldException("doctor.experienceYears");
        }
    }

    private void mustBeNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new MissingRequiredFieldException(field);
        }
    }

    private boolean isUniqueConstraintViolation(DataAccessException e, String constraintName) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(constraintName);
    }
}
