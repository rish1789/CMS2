package com.cms.identity.staff.service;

import com.cms.identity.staff.exception.ForbiddenException;
import com.cms.identity.staff.exception.InvalidPasswordException;
import com.cms.identity.staff.exception.RoleAssignmentNotFoundException;


import com.cms.identity.account.domain.Account;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.AccountRepository;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.account.service.PasswordPolicyValidator;
import com.cms.identity.account.service.TemporaryPasswordGenerator;
import com.cms.identity.staff.dto.ResetStaffPasswordResponse;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * real-bug-fix 2026-09-17: found live while verifying a treating-doctor-only access rule -
 * there was no way for a ClinicAdmin to give a locked-out Doctor or Operations staff member a
 * working login again, only Super Admin could do this and only for a ClinicAdmin account
 * (ClinicVerificationService.resetClinicAdminPassword/setClinicAdminPassword). Mirrors that
 * pair exactly - "generate + hash + return once" / "caller-chosen + policy-checked + hash +
 * return once" - scoped instead to the caller's own clinic, the same "only an active
 * ClinicAdmin for THIS specific clinic" gate StaffOnboardingService/StaffDeactivationService
 * already use.
 *
 * <p>Deliberately excludes a ClinicAdmin target (no override, same phrasing this codebase
 * already uses for other no-override rules) - unlike deactivation, a password reset is a
 * silent full account takeover with nothing else needing to change state first, so letting one
 * ClinicAdmin reset another's password is a materially different, more sensitive capability
 * than anything this feature was asked to cover. That stays Super-Admin-only, unchanged.
 */
@Service
public class StaffPasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(StaffPasswordResetService.class);

    private final RoleAssignmentRepository roleAssignmentRepository;
    private final AccountRepository accountRepository;
    private final TemporaryPasswordGenerator temporaryPasswordGenerator;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final PasswordEncoder passwordEncoder;

    public StaffPasswordResetService(
            RoleAssignmentRepository roleAssignmentRepository,
            AccountRepository accountRepository,
            TemporaryPasswordGenerator temporaryPasswordGenerator,
            PasswordPolicyValidator passwordPolicyValidator,
            PasswordEncoder passwordEncoder) {
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.accountRepository = accountRepository;
        this.temporaryPasswordGenerator = temporaryPasswordGenerator;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public ResetStaffPasswordResponse resetPassword(UUID callerAccountId, UUID clinicId, UUID targetAccountId) {
        Account account = findResettableAccount(callerAccountId, clinicId, targetAccountId);
        String temporaryPassword = temporaryPasswordGenerator.generate();
        account.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        accountRepository.save(account);
        log.info("ClinicAdmin reset staff password: accountId={}, clinicId={}", targetAccountId, clinicId);
        return new ResetStaffPasswordResponse(account.getId(), account.getEmail(), account.getStaffCode(), temporaryPassword);
    }

    @Transactional
    public ResetStaffPasswordResponse setPassword(
            UUID callerAccountId, UUID clinicId, UUID targetAccountId, String newPassword) {
        List<String> failedRules = passwordPolicyValidator.validate(newPassword);
        if (!failedRules.isEmpty()) {
            throw new InvalidPasswordException(failedRules);
        }
        Account account = findResettableAccount(callerAccountId, clinicId, targetAccountId);
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        accountRepository.save(account);
        log.info("ClinicAdmin set staff password: accountId={}, clinicId={}", targetAccountId, clinicId);
        return new ResetStaffPasswordResponse(account.getId(), account.getEmail(), account.getStaffCode(), newPassword);
    }

    private Account findResettableAccount(UUID callerAccountId, UUID clinicId, UUID targetAccountId) {
        if (!roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                callerAccountId, clinicId, RoleAssignment.Role.ClinicAdmin)) {
            throw new ForbiddenException();
        }
        RoleAssignment target = roleAssignmentRepository
                .findByAccount_IdAndClinic_Id(targetAccountId, clinicId)
                .orElseThrow(RoleAssignmentNotFoundException::new);
        if (target.getRole() == RoleAssignment.Role.ClinicAdmin) {
            throw new ForbiddenException();
        }
        return target.getAccount();
    }
}
