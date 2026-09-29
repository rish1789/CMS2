package com.cms.booking.api;

import com.cms.booking.exception.ForbiddenException;
import com.cms.booking.service.SessionDeletionService;


import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.account.config.SecurityConfig;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.exception.SessionNotFoundException;
import com.cms.scheduling.repository.SessionRepository;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** real-bug-fix 2026-09-17: Operations-or-ClinicAdmin only (never the Doctor) - mirrors SessionCancellationController's identical write-action gate exactly. */
@RestController
public class SessionDeletionController {

    private final SessionRepository sessionRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final SessionDeletionService sessionDeletionService;

    public SessionDeletionController(
            SessionRepository sessionRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            SessionDeletionService sessionDeletionService) {
        this.sessionRepository = sessionRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.sessionDeletionService = sessionDeletionService;
    }

    @DeleteMapping("/api/v1/clinics/{clinicId}/sessions/{sessionId}")
    public void delete(@PathVariable UUID clinicId, @PathVariable UUID sessionId, Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        requireAuthorized(callerAccountId, clinicId);

        Session session = sessionRepository
                .findById(sessionId)
                .filter(s -> s.getClinic().getId().equals(clinicId))
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        sessionDeletionService.deleteSession(session);
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
