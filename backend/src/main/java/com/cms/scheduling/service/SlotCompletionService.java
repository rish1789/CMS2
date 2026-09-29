package com.cms.scheduling.service;

import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.scheduling.exception.ForbiddenException;
import com.cms.scheduling.exception.SlotNotCompletableException;
import com.cms.scheduling.exception.SlotNotFoundException;
import com.cms.scheduling.exception.SlotNotYetStartedException;
import com.cms.scheduling.repository.SlotRepository;


import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.doctor.DoctorProfile;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 026: mark a BOOKED Fixed-Time Slot completed, then recalculate its Session's delay figure as
 * part of the same action (FR-001/FR-002). The first-ever "completed" action in this codebase.
 *
 * <p>057-day-sheet-status-overhaul: eligibility is now role-dependent, additively - ClinicAdmin/
 * Operations keep the original BOOKED path unchanged and additionally gain APPEARED; the
 * treating doctor (new caller this feature adds) is only ever eligible from APPEARED (FR-006
 * scopes their access to "their own Appeared slot" specifically - see contracts/
 * day-sheet-status-flow.md).
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

        boolean isStaff = isStaffAuthorized(callerAccountId, clinicId);
        boolean isTreatingDoctor = isTreatingDoctor(slot, callerAccountId);
        if (!isStaff && !isTreatingDoctor) {
            throw new ForbiddenException();
        }

        // 064-queue-send-in-complete (FR-003): no longer Fixed-Time-only - a queue token completes
        // through the same action (its untimed slot skips the not-yet-started check below).

        boolean eligibleStatus = isStaff
                ? (slot.getStatus() == SlotStatus.BOOKED || slot.getStatus() == SlotStatus.APPEARED)
                : slot.getStatus() == SlotStatus.APPEARED;
        if (!eligibleStatus) {
            throw new SlotNotCompletableException(slotId);
        }

        // real-bug-fix 2026-09-16: found live - a slot scheduled for 15:15 was marked completed
        // at 15:03. Nothing previously stopped a slot from being completed before its own
        // scheduled start time even arrived.
        // 063-front-desk-walk-in: an untimed walk-in has no scheduled start to wait for - it's
        // completed whenever the doctor finishes seeing them.
        if (!slot.isUntimed()) {
            LocalDateTime scheduledStart = LocalDateTime.of(slot.getSession().getSessionDate(), slot.getStartTime());
            if (LocalDateTime.now().isBefore(scheduledStart)) {
                throw new SlotNotYetStartedException(slotId);
            }
        }

        slot.setStatus(SlotStatus.COMPLETED);
        sessionDelayService.recalculate(slot.getSession().getId());
        return slot;
    }

    /**
     * 057-day-sheet-status-overhaul: called only by {@link SlotAutoCompletionService}'s sweep -
     * a system action, not a user action, so it carries no authorization check at all
     * (mirroring {@link NoShowDetectionService} having none - research.md Decision 2). The
     * caller is responsible for having already confirmed the slot is APPEARED and past its
     * scheduled end time.
     */
    @Transactional
    public void completeSlotAutomatically(Slot slot) {
        slot.setStatus(SlotStatus.COMPLETED);
        slotRepository.save(slot);
        sessionDelayService.recalculate(slot.getSession().getId());
    }

    /** research.md R7: Operations/ClinicAdmin only (Clarifications) - a new method, deliberately not ScheduleService's doctor-inclusive requireAuthorized. */
    private boolean isStaffAuthorized(UUID callerAccountId, UUID clinicId) {
        boolean isOperations = roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                callerAccountId, clinicId, RoleAssignment.Role.Operations);
        boolean isClinicAdmin = roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                callerAccountId, clinicId, RoleAssignment.Role.ClinicAdmin);

        return isOperations || isClinicAdmin;
    }

    /**
     * 057 research.md Decision 5: a local, scheduling-owned check - not reused from
     * com.cms.clinical.service.TreatingDoctorAuthorizationService, which would create a
     * scheduling -&gt; clinical -&gt; booking -&gt; scheduling module dependency cycle.
     * scheduling already directly owns the Slot -&gt; Session -&gt; DoctorProfile relationship
     * this needs.
     */
    private boolean isTreatingDoctor(Slot slot, UUID callerAccountId) {
        DoctorProfile treatingDoctor = slot.getSession().getDoctorProfile();
        return treatingDoctor.getAccount().getId().equals(callerAccountId);
    }
}
