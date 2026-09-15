package com.cms.booking;

import com.cms.booking.dto.PartialCancellationRequest;
import com.cms.booking.dto.SessionCancellationResponse;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.identity.account.SecurityConfig;
import com.cms.scheduling.Session;
import com.cms.scheduling.SessionNotFoundException;
import com.cms.scheduling.SessionRepository;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 030: Operations-or-ClinicAdmin only (never the Doctor) - mirrors 029's identical write-action gate. */
@RestController
public class SessionPartialCancellationController {

    private final SessionRepository sessionRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final SessionPartialCancellationService sessionPartialCancellationService;

    public SessionPartialCancellationController(
            SessionRepository sessionRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            SessionPartialCancellationService sessionPartialCancellationService) {
        this.sessionRepository = sessionRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.sessionPartialCancellationService = sessionPartialCancellationService;
    }

    @PostMapping("/api/v1/clinics/{clinicId}/sessions/{sessionId}/cancel-from-cutoff")
    public SessionCancellationResponse cancelFromCutoff(
            @PathVariable UUID clinicId,
            @PathVariable UUID sessionId,
            @RequestBody PartialCancellationRequest request,
            Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        requireAuthorized(callerAccountId, clinicId);

        Session session = sessionRepository
                .findById(sessionId)
                .filter(s -> s.getClinic().getId().equals(clinicId))
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        int cancelledCount =
                sessionPartialCancellationService.cancelFromCutoff(session, request.cutoffTime(), request.toTime());
        return new SessionCancellationResponse(sessionId, cancelledCount);
    }

    /** Mirrors 016/020/025/028/029's identical Operations-or-ClinicAdmin write-action gate. */
    private void requireAuthorized(UUID callerAccountId, UUID clinicId) {
        boolean isOperations = roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                callerAccountId, clinicId, RoleAssignment.Role.Operations);
        boolean isClinicAdmin = roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                callerAccountId, clinicId, RoleAssignment.Role.ClinicAdmin);

        if (!isOperations && !isClinicAdmin) {
            throw new ForbiddenException();
        }
    }
}
