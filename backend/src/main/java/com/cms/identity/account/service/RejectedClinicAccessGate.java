package com.cms.identity.account.service;

import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 062-rejected-clinic-gating (FR-007, research.md Decision 6): the per-request decision behind
 * {@code RejectedClinicAccessInterceptor}. Only a rejected clinic is ever gated - every other
 * clinic costs one primary-key read and nothing more - and at a rejected clinic only an active
 * ClinicAdmin role gets through. A caller with no role there at all (or an unknown clinic) is
 * deliberately allowed past this gate: the existing per-service "not staffed"/"not found" checks
 * keep answering those cases exactly as they did before this feature. Tenant-scoped by
 * construction (FR-008): it reads only the clinic the request names.
 */
@Service
public class RejectedClinicAccessGate {

    private final ClinicRepository clinicRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;

    public RejectedClinicAccessGate(ClinicRepository clinicRepository, RoleAssignmentRepository roleAssignmentRepository) {
        this.clinicRepository = clinicRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    @Transactional(readOnly = true)
    public boolean allows(UUID accountId, UUID clinicId) {
        boolean rejected = clinicRepository.findById(clinicId).map(Clinic::isRejected).orElse(false);
        if (!rejected) {
            return true;
        }
        List<RoleAssignment> roles = roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(accountId, clinicId);
        return roles.isEmpty() || roles.stream().anyMatch(role -> role.getRole() == RoleAssignment.Role.ClinicAdmin);
    }
}
