package com.cms.scheduling.service;

import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.scheduling.exception.ForbiddenException;
import com.cms.scheduling.exception.SlotNotAppearableException;
import com.cms.scheduling.exception.SlotNotFoundException;
import com.cms.scheduling.repository.SlotRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 057-day-sheet-status-overhaul: marks a Slot Appeared - the one action that both starts the
 * normal BOOKED -&gt; APPEARED -&gt; COMPLETED flow and corrects a mistaken automatic NO_SHOW
 * (research.md Decision 3). ClinicAdmin/Operations only, mirroring {@link SlotCompletionService}'s
 * requireAuthorized pattern - deliberately no doctor branch here, unlike that service (FR-007).
 */
@Service
public class SlotAppearedService {

    private final SlotRepository slotRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;

    public SlotAppearedService(SlotRepository slotRepository, RoleAssignmentRepository roleAssignmentRepository) {
        this.slotRepository = slotRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    @Transactional
    public Slot markAppeared(UUID callerAccountId, UUID clinicId, UUID slotId) {
        Slot slot = slotRepository
                .findById(slotId)
                .filter(s -> s.getSession().getClinic().getId().equals(clinicId))
                .orElseThrow(() -> new SlotNotFoundException(slotId));

        requireAuthorized(callerAccountId, clinicId);

        // 064-queue-send-in-complete (FR-002): no longer Fixed-Time-only - a waiting queue token is
        // sent in exactly like an appointment slot.

        if (slot.getStatus() != SlotStatus.BOOKED && slot.getStatus() != SlotStatus.NO_SHOW) {
            throw new SlotNotAppearableException(slotId);
        }

        slot.setStatus(SlotStatus.APPEARED);
        return slot;
    }

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
