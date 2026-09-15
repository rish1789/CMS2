package com.cms.booking;

import com.cms.booking.dto.QueuePositionResponse;
import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.identity.account.SecurityConfig;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 027: any active role at the clinic may query - no Operations/ClinicAdmin-only gate (research.md R4). */
@RestController
public class StaffQueuePositionController {

    private final BookingRepository bookingRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final QueuePositionService queuePositionService;

    public StaffQueuePositionController(
            BookingRepository bookingRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            QueuePositionService queuePositionService) {
        this.bookingRepository = bookingRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.queuePositionService = queuePositionService;
    }

    @GetMapping("/api/v1/clinics/{clinicId}/bookings/{bookingId}/queue-position")
    public QueuePositionResponse queuePosition(
            @PathVariable UUID clinicId, @PathVariable UUID bookingId, Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);

        // analyze finding E1: a valid staff token with zero role assignment at this clinic
        // is rejected 403, mirroring every other staff-gated action in this codebase - not
        // silently folded into the 404 below.
        if (!roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId)) {
            throw new ForbiddenException();
        }

        Booking booking = bookingRepository
                .findById(bookingId)
                .filter(b -> b.getSlot().getSession().getClinic().getId().equals(clinicId))
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        QueuePositionService.QueuePosition position = queuePositionService.positionOf(booking);
        return new QueuePositionResponse(bookingId, position.applicable(), position.position());
    }
}
