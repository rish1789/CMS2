package com.cms.patient.record;

import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.identity.account.SecurityConfig;
import com.cms.patient.record.dto.PatientSearchResultResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * staff-console-audit-2026-09-10 P1: read-only "who is this patient" lookup by id - backs the
 * entity-context header on the Anonymize page, which previously showed only a bare red button
 * with no indication of whose record it was about to erase. Reuses PatientSearchResultResponse
 * (same three fields) rather than inventing a near-duplicate DTO.
 */
@RestController
public class PatientDetailController {

    private final PatientRepository patientRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;

    public PatientDetailController(
            PatientRepository patientRepository, RoleAssignmentRepository roleAssignmentRepository) {
        this.patientRepository = patientRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    @GetMapping("/api/v1/clinics/{clinicId}/patients/{patientId}")
    public PatientSearchResultResponse detail(
            @PathVariable UUID clinicId, @PathVariable UUID patientId, Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        if (!roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId)) {
            throw new NotStaffedAtClinicException();
        }

        Patient patient = patientRepository
                .findById(patientId)
                .filter(p -> p.getClinic().getId().equals(clinicId))
                .orElseThrow(() -> new PatientNotFoundException(patientId));

        return PatientSearchResultResponse.from(patient);
    }
}
