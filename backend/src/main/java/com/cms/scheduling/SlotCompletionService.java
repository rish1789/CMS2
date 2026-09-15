package com.cms.scheduling;

import com.cms.identity.account.RoleAssignment;
import com.cms.identity.account.RoleAssignmentRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 026: mark a BOOKED Fixed-Time Slot completed, then recalculate its Session's delay figure as
 * part of the same action (FR-001/FR-002). The first-ever "completed" action in this codebase.
 */
@Service
public class SlotCompletionService {

    private final SlotRepository slotRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final SessionDelayService sessionDelayService;

    public SlotCompletionService(
            SlotRepository slotRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            SessionDelayService sessionDelayService) {
        this.slotRepository = slotRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.sessionDelayService = sessionDelayService;
    }

    @Transactional
    public Slot completeSlot(UUID callerAccountId, UUID clinicId, UUID slotId) {
        Slot slot = slotRepository
                .findById(slotId)
                .filter(s -> s.getSession().getClinic().getId().equals(clinicId))
                .orElseThrow(() -> new SlotNotFoundException(slotId));

        requireAuthorized(callerAccountId, clinicId);

        // spec Assumptions/Edge Cases: Fixed-Time-only, mirroring 025's identical
        // load -> authorize -> mode-check ordering and reused exception.
        if (slot.getSession().getMode() != ScheduleMode.FIXED_TIME) {
            throw new NotAFixedTimeSessionException(slot.getSession().getId());
        }

        if (slot.getStatus() != SlotStatus.BOOKED) {
            throw new SlotNotCompletableException(slotId);
        }

        slot.setStatus(SlotStatus.COMPLETED);
        sessionDelayService.recalculate(slot.getSession().getId());
        return slot;
    }

    /** research.md R7: Operations/ClinicAdmin only (Clarifications) - a new method, deliberately not ScheduleService's doctor-inclusive requireAuthorized. */
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
