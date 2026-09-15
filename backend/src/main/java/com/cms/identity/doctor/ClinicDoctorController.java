package com.cms.identity.doctor;

import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.identity.account.SecurityConfig;
import com.cms.identity.doctor.dto.DoctorListResponse;
import com.cms.identity.doctor.dto.DoctorSummaryResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 041-staff-console-pickers FR-006: doctors staffed at a clinic, replacing a typed Doctor Profile ID. */
@RestController
@RequestMapping("/api/v1/clinics/{clinicId}/doctors")
public class ClinicDoctorController {

    private final DoctorProfileRepository doctorProfileRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;

    public ClinicDoctorController(
            DoctorProfileRepository doctorProfileRepository, RoleAssignmentRepository roleAssignmentRepository) {
        this.doctorProfileRepository = doctorProfileRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    private static final int DEFAULT_PAGE_SIZE = 20;

    @GetMapping
    public DoctorListResponse list(
            @PathVariable UUID clinicId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        if (!roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId)) {
            throw new NotStaffedAtClinicException();
        }
        String searchPattern = (q == null || q.isBlank()) ? null : "%" + q.trim().toLowerCase() + "%";
        Page<DoctorProfile> doctorPage =
                doctorProfileRepository.findByClinicStaffed(clinicId, searchPattern, PageRequest.of(page, size));
        var doctors = doctorPage.getContent().stream().map(DoctorSummaryResponse::from).toList();
        return new DoctorListResponse(doctors, page, size, doctorPage.getTotalElements());
    }
}
