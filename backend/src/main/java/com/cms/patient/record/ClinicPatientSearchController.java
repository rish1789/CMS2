package com.cms.patient.record;

import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.identity.account.SecurityConfig;
import com.cms.patient.record.dto.PatientSearchListResponse;
import com.cms.patient.record.dto.PatientSearchResultResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 041-staff-console-pickers FR-007: patient search by name or phone, replacing a typed Patient ID. */
@RestController
@RequestMapping("/api/v1/clinics/{clinicId}/patients/search")
public class ClinicPatientSearchController {

    private static final int MIN_TERM_LENGTH = 2;

    private final PatientRepository patientRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;

    public ClinicPatientSearchController(
            PatientRepository patientRepository, RoleAssignmentRepository roleAssignmentRepository) {
        this.patientRepository = patientRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    private static final int DEFAULT_PAGE_SIZE = 20;

    @GetMapping
    public PatientSearchListResponse search(
            @PathVariable UUID clinicId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        if (!roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId)) {
            throw new NotStaffedAtClinicException();
        }
        if (q == null || q.trim().length() < MIN_TERM_LENGTH) {
            throw new InvalidSearchTermException();
        }
        Page<Patient> patientPage = patientRepository.search(clinicId, q.trim(), PageRequest.of(page, size));
        var patients = patientPage.getContent().stream().map(PatientSearchResultResponse::from).toList();
        return new PatientSearchListResponse(patients, page, size, patientPage.getTotalElements());
    }
}
