package com.cms.identity.staff;

import com.cms.identity.account.RoleAssignment;
import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.identity.account.SecurityConfig;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.identity.staff.dto.StaffListResponse;
import com.cms.identity.staff.dto.StaffSummaryResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 041-staff-console-pickers FR-006a: active staff at a clinic (any role), replacing a typed Account ID.
 *
 * <p>pagination-unification-2026-09-10: search/role/status/specialization filtering, sorting,
 * and paging all moved server-side (see {@link RoleAssignmentRepository#search}) - the Roster
 * page's Prev/Next/Jump-to-page UI used to look server-paginated while this endpoint silently
 * returned every RoleAssignment at the clinic in one response.
 */
@RestController
@RequestMapping("/api/v1/clinics/{clinicId}/staff")
public class ClinicStaffController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final RoleAssignmentRepository roleAssignmentRepository;
    private final DoctorProfileRepository doctorProfileRepository;

    public ClinicStaffController(
            RoleAssignmentRepository roleAssignmentRepository, DoctorProfileRepository doctorProfileRepository) {
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.doctorProfileRepository = doctorProfileRepository;
    }

    @GetMapping
    public StaffListResponse list(
            @PathVariable UUID clinicId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) RoleAssignment.Role role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String specialization,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String sortDir,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        if (!roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId)) {
            throw new NotStaffedAtClinicException();
        }
        // Pre-formatted here, not via CONCAT in JPQL - see RoleAssignmentRepository.search's own
        // doc comment for why a null bound parameter inside CONCAT fails Postgres' type inference.
        String searchPattern = (q == null || q.isBlank()) ? null : "%" + q.trim().toLowerCase() + "%";
        Sort sort = resolveSort(sortBy, sortDir);

        // Status-filter follow-up: includes inactive Role Assignments too (unless the caller
        // explicitly narrows to one status) - the original active-only scope, now opt-in.
        Page<RoleAssignment> roleAssignmentPage = roleAssignmentRepository.search(
                clinicId, role, active, specialization, searchPattern, PageRequest.of(page, size, sort));

        // One bulk lookup for every row's DoctorProfile (specialization/experience), instead of
        // one query per row - ClinicAdmin/Operations accounts simply have no entry in this map.
        List<UUID> accountIds = roleAssignmentPage.getContent().stream()
                .map(ra -> ra.getAccount().getId())
                .toList();
        Map<UUID, DoctorProfile> doctorProfilesByAccountId = doctorProfileRepository.findByAccount_IdIn(accountIds)
                .stream()
                .collect(Collectors.toMap(dp -> dp.getAccount().getId(), Function.identity()));

        var staff = roleAssignmentPage.getContent().stream()
                .map(ra -> StaffSummaryResponse.from(
                        ra, doctorProfilesByAccountId.get(ra.getAccount().getId())))
                .toList();
        List<String> specializations = doctorProfileRepository.findDistinctSpecializationsByClinic(clinicId);
        return new StaffListResponse(staff, page, size, roleAssignmentPage.getTotalElements(), specializations);
    }

    /**
     * {@code specialization}/{@code joinedAt} aren't properties of {@link RoleAssignment} itself
     * (specialization lives on the {@code dp} alias joined in by {@link
     * RoleAssignmentRepository#search}; joinedAt is exposed to the frontend as a computed name
     * for {@code createdAt}) - {@link Sort#by} validates property names against the entity's own
     * JPA metamodel and would reject both, so this uses {@link JpaSort#unsafe} to pass the
     * property expression straight through to the query's ORDER BY clause instead.
     */
    private Sort resolveSort(String sortBy, String sortDir) {
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        String property =
                switch (sortBy == null ? "" : sortBy) {
                    case "experienceYears" -> "dp.experienceYears";
                    case "joinedAt" -> "ra.createdAt";
                    default -> "ra.account.name"; // "name", unrecognized, or absent - the roster's natural default order
                };
        return JpaSort.unsafe(direction, property);
    }
}
