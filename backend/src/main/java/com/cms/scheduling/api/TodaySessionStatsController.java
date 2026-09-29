package com.cms.scheduling.api;

import com.cms.identity.account.config.SecurityConfig;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.scheduling.dto.TodaySessionStatsResponse;
import com.cms.scheduling.exception.NotStaffedAtClinicException;
import com.cms.scheduling.repository.SlotRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 051-staff-dashboard-enhancement T009 (contracts/today-session-stats.md): today's
 * completed/no-show Slot counts for the staff dashboard's "Today's stats" tile. Reuses
 * ClinicSessionListController's exact "any active role at this clinic" authorization gate
 * (FR-009).
 *
 * <p>doctor-console-cross-doctor-leak fix: also reuses ClinicSessionListController's doctor
 * self-scoping - a caller whose only active role at this clinic is Doctor sees only their own
 * completed/no-show counts here, not the whole clinic's (this tile has no "at this clinic"
 * qualifier in its UI label, so a clinic-wide number read as the caller's own personal count).
 * A caller who also holds ClinicAdmin or Operations at this clinic keeps seeing every doctor's
 * counts, unchanged.
 */
@RestController
@RequestMapping("/api/v1/clinics/{clinicId}/sessions/today-stats")
public class TodaySessionStatsController {

    private final SlotRepository slotRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final DoctorProfileRepository doctorProfileRepository;

    public TodaySessionStatsController(
            SlotRepository slotRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            DoctorProfileRepository doctorProfileRepository) {
        this.slotRepository = slotRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.doctorProfileRepository = doctorProfileRepository;
    }

    @GetMapping
    public TodaySessionStatsResponse get(@PathVariable UUID clinicId, Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        List<RoleAssignment> roles =
                roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId);
        if (roles.isEmpty()) {
            throw new NotStaffedAtClinicException();
        }
        boolean doctorOnly = roles.stream().noneMatch(ra -> ra.getRole() != RoleAssignment.Role.Doctor);

        UUID selfScopeDoctorProfileId = null;
        if (doctorOnly) {
            selfScopeDoctorProfileId = doctorProfileRepository
                    .findByAccount_Id(callerAccountId)
                    .map(dp -> dp.getId())
                    .orElse(null);
            if (selfScopeDoctorProfileId == null) {
                // Doctor-only role with no resolvable DoctorProfile - fail closed (all-zero
                // counts) rather than accidentally dropping the self-scope restriction.
                return TodaySessionStatsResponse.from(List.of());
            }
        }

        return TodaySessionStatsResponse.from(
                slotRepository.countStatusByClinicAndDate(clinicId, LocalDate.now(), selfScopeDoctorProfileId));
    }
}
