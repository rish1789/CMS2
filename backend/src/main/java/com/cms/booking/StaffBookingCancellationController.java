package com.cms.booking;

import com.cms.booking.dto.BookingResponse;
import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.identity.account.SecurityConfig;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 028: any active role at the clinic may cancel - no time restriction, unlike patient self-service (research.md R5/R6). */
@RestController
public class StaffBookingCancellationController {

    private final BookingRepository bookingRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final BookingCancellationService bookingCancellationService;

    public StaffBookingCancellationController(
            BookingRepository bookingRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            BookingCancellationService bookingCancellationService) {
        this.bookingRepository = bookingRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.bookingCancellationService = bookingCancellationService;
    }

    @PostMapping("/api/v1/clinics/{clinicId}/bookings/{bookingId}/cancel")
    public BookingResponse cancel(@PathVariable UUID clinicId, @PathVariable UUID bookingId, Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);

        // mirrors 027's identical E1-fixed pattern: zero role at this clinic is 403, not folded into 404.
        if (!roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId)) {
            throw new ForbiddenException();
        }

        Booking booking = bookingRepository
                .findById(bookingId)
                .filter(b -> b.getSlot().getSession().getClinic().getId().equals(clinicId))
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        return BookingResponse.of(bookingCancellationService.cancel(booking));
    }
}
