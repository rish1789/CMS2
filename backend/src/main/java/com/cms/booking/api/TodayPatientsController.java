package com.cms.booking.api;

import com.cms.booking.dto.TodayPatientResponse;
import com.cms.booking.exception.NotStaffedAtClinicException;
import com.cms.booking.repository.BookingRepository;


import com.cms.identity.account.config.SecurityConfig;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.doctor.DoctorProfileRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * real-bug-fix 2026-09-17: the Find a Patient page's default "today's patients" table -
 * replacing an empty page with nothing to look at until staff types a search term. Same
 * "any active role at this clinic" gate as {@code ClinicPatientSearchController} (this is a
 * read-only roster view, not a write action restricted to a subset of roles).
 *
 * <p>doctor-console-cross-doctor-leak fix: also reuses ClinicSessionListController's doctor
 * self-scoping - a caller whose only active role at this clinic is Doctor sees only their own
 * patients here, not every doctor's at the clinic (this page is one click from a Doctor's home
 * console, so an unscoped clinic-wide roster let a doctor see another doctor's patient's name,
 * phone, and open their full record). A caller who also holds ClinicAdmin or Operations at this
 * clinic keeps seeing every doctor's patients, unchanged.
 */
@RestController
@RequestMapping("/api/v1/clinics/{clinicId}/patients/today")
public class TodayPatientsController {

    private final BookingRepository bookingRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final DoctorProfileRepository doctorProfileRepository;

    public TodayPatientsController(
            BookingRepository bookingRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            DoctorProfileRepository doctorProfileRepository) {
        this.bookingRepository = bookingRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.doctorProfileRepository = doctorProfileRepository;
    }

    @GetMapping
    public List<TodayPatientResponse> today(@PathVariable UUID clinicId, Authentication authentication) {
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
                // Doctor-only role with no resolvable DoctorProfile - fail closed (empty
                // roster) rather than accidentally dropping the self-scope restriction.
                return List.of();
            }
        }

        return bookingRepository
                .findActiveByClinicAndSessionDate(clinicId, LocalDate.now(), selfScopeDoctorProfileId).stream()
                .map(TodayPatientResponse::from)
                .toList();
    }
}
