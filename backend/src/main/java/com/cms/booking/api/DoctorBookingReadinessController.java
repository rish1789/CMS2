package com.cms.booking.api;

import com.cms.booking.dto.DoctorBookingReadinessResponse;
import com.cms.booking.exception.NotStaffedAtClinicException;
import com.cms.booking.service.DoctorBookingReadinessService;


import com.cms.identity.account.config.SecurityConfig;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * real-bug-fix 2026-09-17: backs the staff-console Doctors page's "booking setup incomplete"
 * warning - see DoctorBookingReadinessResponse's own Javadoc. Any active staff role at the
 * clinic may view this (read-only, same gate as ClinicDoctorController's own doctor listing).
 */
@RestController
@RequestMapping("/api/v1/clinics/{clinicId}/doctors/booking-readiness")
public class DoctorBookingReadinessController {

    private final DoctorBookingReadinessService doctorBookingReadinessService;
    private final RoleAssignmentRepository roleAssignmentRepository;

    public DoctorBookingReadinessController(
            DoctorBookingReadinessService doctorBookingReadinessService,
            RoleAssignmentRepository roleAssignmentRepository) {
        this.doctorBookingReadinessService = doctorBookingReadinessService;
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    @GetMapping
    public List<DoctorBookingReadinessResponse> list(@PathVariable UUID clinicId, Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        if (!roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId)) {
            throw new NotStaffedAtClinicException();
        }
        return doctorBookingReadinessService.forClinic(clinicId);
    }
}
