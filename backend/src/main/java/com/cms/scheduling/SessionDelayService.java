package com.cms.scheduling;

import com.cms.identity.account.RoleAssignmentRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 026: recalculates and stores a Fixed-Time Session's delay figure - called from exactly two
 * trigger points ({@link SlotCompletionService}, and 025's {@code WalkInInsertionService}) - and
 * reads it back verbatim, never recomputing. See data-model.md and research.md R2/R4/R5.
 */
@Service
public class SessionDelayService {

    private final SessionRepository sessionRepository;
    private final SlotRepository slotRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;

    public SessionDelayService(
            SessionRepository sessionRepository,
            SlotRepository slotRepository,
            RoleAssignmentRepository roleAssignmentRepository) {
        this.sessionRepository = sessionRepository;
        this.slotRepository = slotRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    /**
     * FR-002/FR-003/FR-004: overwrites the stored delay with the minutes between now and the
     * scheduled time of the earliest still-OPEN/BOOKED Slot whose scheduled time has passed;
     * {@code null} if no such Slot exists. A no-op for a Queue-mode Session (it never carries a
     * delay figure - FR-007) - callers are expected to only invoke this for Fixed-Time Sessions,
     * but this method stays defensively correct either way.
     */
    @Transactional
    public void recalculate(UUID sessionId) {
        Session session = sessionRepository.findById(sessionId).orElseThrow(() -> new SessionNotFoundException(sessionId));
        if (session.getMode() != ScheduleMode.FIXED_TIME) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<Slot> slots = slotRepository.findBySession_Id(sessionId);

        Optional<Slot> earliestUnresolvedPastDue = slots.stream()
                .filter(s -> s.getStatus() == SlotStatus.OPEN || s.getStatus() == SlotStatus.BOOKED)
                .filter(s -> s.getStartTime() != null)
                .filter(s -> LocalDateTime.of(session.getSessionDate(), s.getStartTime()).isBefore(now))
                .min(Comparator.comparing(Slot::getStartTime));

        Integer delayMinutes = earliestUnresolvedPastDue
                .map(s -> (int) Duration.between(LocalDateTime.of(session.getSessionDate(), s.getStartTime()), now)
                        .toMinutes())
                .orElse(null);

        session.setDelayMinutes(delayMinutes);
    }

    /**
     * FR-005/FR-006: a pure read - never recomputes. FR-007: {@code applicable=false} for a
     * Queue-mode Session, {@code delayMinutes} always null alongside it.
     *
     * <p>_diagnostics [CRITICAL] - [full-repo-audit] - [CROSS_CLINIC_LEAK]: any active role at
     * {@code clinicId} - "staff or the doctor may both view" (FR-006) means no restriction on
     * WHICH role, not no restriction on WHICH clinic.
     */
    @Transactional(readOnly = true)
    public SessionDelay currentDelay(UUID callerAccountId, UUID clinicId, UUID sessionId) {
        if (!roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId)) {
            throw new NotStaffedAtClinicException();
        }
        Session session = sessionRepository
                .findById(sessionId)
                .filter(s -> s.getClinic().getId().equals(clinicId))
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
        if (session.getMode() != ScheduleMode.FIXED_TIME) {
            return new SessionDelay(false, null);
        }
        return new SessionDelay(true, session.getDelayMinutes());
    }

    public record SessionDelay(boolean applicable, Integer delayMinutes) {}
}
