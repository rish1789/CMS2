package com.cms.booking;

import com.cms.booking.dto.BookingDetailResponse;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.identity.account.SecurityConfig;
import com.cms.identity.doctor.DoctorProfileRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * staff-console-audit-2026-09-10 P1: read-only "who/what is this booking" lookup - mirrors
 * SessionDaySheetController's authorization shape exactly (any active role at the clinic;
 * Doctor-only callers are further scoped to their own bookings, a wrong-clinic/wrong-doctor
 * result looking identical to a nonexistent one).
 */
@RestController
public class BookingDetailController {

    private final BookingRepository bookingRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final DoctorProfileRepository doctorProfileRepository;

    public BookingDetailController(
            BookingRepository bookingRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            DoctorProfileRepository doctorProfileRepository) {
        this.bookingRepository = bookingRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.doctorProfileRepository = doctorProfileRepository;
    }

    @GetMapping("/api/v1/clinics/{clinicId}/bookings/{bookingId}")
    public BookingDetailResponse detail(
            @PathVariable UUID clinicId, @PathVariable UUID bookingId, Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        List<RoleAssignment> roles =
                roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId);
        if (roles.isEmpty()) {
            throw new NotStaffedAtClinicException();
        }
        boolean doctorOnly = roles.stream().noneMatch(ra -> ra.getRole() != RoleAssignment.Role.Doctor);

        Booking booking = bookingRepository
                .findById(bookingId)
                .filter(b -> b.getSlot().getSession().getClinic().getId().equals(clinicId))
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (doctorOnly) {
            UUID myDoctorProfileId =
                    doctorProfileRepository.findByAccount_Id(callerAccountId).map(dp -> dp.getId()).orElse(null);
            if (!booking.getSlot().getSession().getDoctorProfile().getId().equals(myDoctorProfileId)) {
                throw new BookingNotFoundException(bookingId);
            }
        }

        return BookingDetailResponse.from(booking);
    }
}
