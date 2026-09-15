package com.cms.patient.record;

import com.cms.patient.account.SecurityConfig;
import com.cms.patient.record.dto.PatientClinicListResponse;
import com.cms.patient.record.dto.PatientClinicSummaryResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * patient-booking-flow-rebuild: "My clinics" - every clinic the caller has a linked Patient
 * record at, replacing the "type a Clinic ID" entry point on the patient dashboard. Mirrors
 * {@code StaffClinicController}'s "/mine" shape on the staff side.
 */
@RestController
public class PatientClinicController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final PatientRepository patientRepository;

    public PatientClinicController(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    @GetMapping("/api/v1/patients/clinics")
    public PatientClinicListResponse mine(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            Authentication authentication) {
        UUID patientAccountId = SecurityConfig.currentPatientAccountId(authentication);
        Page<Patient> patientPage = patientRepository.findByPatientAccount_IdAndAnonymizedAtIsNullOrderByCreatedAtDesc(
                patientAccountId, PageRequest.of(page, size));
        var clinics = patientPage.getContent().stream().map(PatientClinicSummaryResponse::of).toList();
        return new PatientClinicListResponse(clinics, page, size, patientPage.getTotalElements());
    }
}
