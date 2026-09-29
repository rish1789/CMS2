package com.cms.booking.api;

import com.cms.booking.dto.BatchCancelRequest;
import com.cms.booking.dto.BatchCancelResponse;
import com.cms.booking.exception.ForbiddenException;
import com.cms.booking.service.BatchBookingCancellationService;

import com.cms.identity.account.config.SecurityConfig;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 057-day-sheet-status-overhaul (contracts/day-sheet-status-flow.md, FR-015): ClinicAdmin/
 * Operations only - deliberately narrower than {@link StaffBookingCancellationController}'s
 * existing "any active role" authorization for a single booking (research.md Decision 7). Does
 * not change that endpoint's own authorization.
 */
@RestController
public class BatchBookingCancellationController {

    private final RoleAssignmentRepository roleAssignmentRepository;
    private final BatchBookingCancellationService batchBookingCancellationService;

    public BatchBookingCancellationController(
            RoleAssignmentRepository roleAssignmentRepository,
            BatchBookingCancellationService batchBookingCancellationService) {
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.batchBookingCancellationService = batchBookingCancellationService;
    }

    @PostMapping("/api/v1/clinics/{clinicId}/sessions/{sessionId}/bookings/cancel-batch")
    public BatchCancelResponse cancelBatch(
            @PathVariable UUID clinicId,
            @PathVariable UUID sessionId,
            @RequestBody BatchCancelRequest request,
            Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        requireStaffAuthorized(callerAccountId, clinicId);
        return batchBookingCancellationService.cancelAll(clinicId, request.bookingIds());
    }

    private void requireStaffAuthorized(UUID callerAccountId, UUID clinicId) {
        boolean isOperations = roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                callerAccountId, clinicId, RoleAssignment.Role.Operations);
        boolean isClinicAdmin = roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                callerAccountId, clinicId, RoleAssignment.Role.ClinicAdmin);

        if (!isOperations && !isClinicAdmin) {
            throw new ForbiddenException();
        }
    }
}
