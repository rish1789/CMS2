package com.cms.identity.staff;

import com.cms.identity.account.RoleAssignment;
import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.identity.staff.dto.DeactivateStaffResponse;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements FR-001..FR-008: an authenticated ClinicAdmin deactivates a Role Assignment
 * (Doctor, Operations, or ClinicAdmin) at their own clinic. Deactivating a ClinicAdmin
 * Role Assignment is blocked - with no override for any role - if it would leave the
 * clinic with zero active ClinicAdmins.
 */
@Service
public class StaffDeactivationService {

    private static final Logger log = LoggerFactory.getLogger(StaffDeactivationService.class);

    private final RoleAssignmentRepository roleAssignmentRepository;

    public StaffDeactivationService(RoleAssignmentRepository roleAssignmentRepository) {
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    @Transactional
    public DeactivateStaffResponse deactivate(UUID callerAccountId, UUID clinicId, UUID targetAccountId, String reasonValue) {
        // FR-002: only an active ClinicAdmin for THIS specific clinic may deactivate staff
        // here - same per-path-variable pattern as 004's StaffOnboardingService.
        if (!roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                callerAccountId, clinicId, RoleAssignment.Role.ClinicAdmin)) {
            throw new ForbiddenException();
        }

        RoleAssignment target = roleAssignmentRepository
                .findByAccount_IdAndClinic_Id(targetAccountId, clinicId)
                .orElseThrow(RoleAssignmentNotFoundException::new);

        // FR-007: idempotent - an already-inactive target is a no-op success, and
        // specifically must NOT re-run the last-active check (which would be meaningless
        // against an already-deactivated row) or any other side effect - including reason
        // validation, since nothing is actually being written in that case.
        if (target.isActive()) {
            if (target.getRole() == RoleAssignment.Role.ClinicAdmin) {
                // FR-003/FR-004/FR-008: scoped to THIS clinic only (research.md). If this
                // is the only active ClinicAdmin left, block - no override for any role.
                long activeClinicAdmins = roleAssignmentRepository.countByClinic_IdAndRoleAndActiveTrue(
                        clinicId, RoleAssignment.Role.ClinicAdmin);
                if (activeClinicAdmins <= 1) {
                    throw new LastActiveClinicAdminException();
                }
            }
            target.deactivate(parseReason(reasonValue));
            roleAssignmentRepository.save(target);
            log.info(
                    "Staff Role Assignment deactivated: accountId={}, clinicId={}, role={}, reason={}",
                    targetAccountId,
                    clinicId,
                    target.getRole(),
                    reasonValue);
        }

        return new DeactivateStaffResponse(targetAccountId, clinicId, target.getRole().name(), target.isActive());
    }

    private RoleAssignment.DeactivationReason parseReason(String reasonValue) {
        if (reasonValue == null || reasonValue.isBlank()) {
            throw new MissingDeactivationReasonException();
        }
        try {
            return RoleAssignment.DeactivationReason.valueOf(reasonValue);
        } catch (IllegalArgumentException e) {
            throw new InvalidDeactivationReasonException();
        }
    }
}
