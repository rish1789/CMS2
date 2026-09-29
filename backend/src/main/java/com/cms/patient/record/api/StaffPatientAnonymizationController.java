package com.cms.patient.record.api;

import com.cms.patient.record.domain.Patient;
import com.cms.patient.record.exception.ForbiddenException;
import com.cms.patient.record.service.PatientAnonymizationService;


import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.account.config.SecurityConfig;
import com.cms.patient.record.dto.PatientAnonymizationResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 037: Operations-or-ClinicAdmin only, mirrors 016/020/025/029/030's identical standard write-action gate (research.md R7). */
@RestController
public class StaffPatientAnonymizationController {

    private final RoleAssignmentRepository roleAssignmentRepository;
    private final PatientAnonymizationService patientAnonymizationService;

    public StaffPatientAnonymizationController(
            RoleAssignmentRepository roleAssignmentRepository, PatientAnonymizationService patientAnonymizationService) {
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.patientAnonymizationService = patientAnonymizationService;
    }

    @PostMapping("/api/v1/clinics/{clinicId}/patients/{patientId}/anonymize")
    public PatientAnonymizationResponse anonymize(
            @PathVariable UUID clinicId, @PathVariable UUID patientId, Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        requireAuthorized(callerAccountId, clinicId);

        Patient patient = patientAnonymizationService.anonymize(clinicId, patientId);
        return PatientAnonymizationResponse.of(patient);
    }

    /** Mirrors 016/020/025/029/030's identical Operations-or-ClinicAdmin write-action gate. */
    private void requireAuthorized(UUID callerAccountId, UUID clinicId) {
        boolean isOperations = roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                callerAccountId, clinicId, RoleAssignment.Role.Operations);
        boolean isClinicAdmin = roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                callerAccountId, clinicId, RoleAssignment.Role.ClinicAdmin);

        if (!isOperations && !isClinicAdmin) {
            throw new ForbiddenException();
        }
    }
}
