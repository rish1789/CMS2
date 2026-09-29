package com.cms.scheduling.service;

import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.scheduling.exception.NotStaffedAtClinicException;
import com.cms.scheduling.exception.SessionNotFoundException;
import com.cms.scheduling.repository.SessionRepository;
import com.cms.scheduling.repository.SlotRepository;


import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.doctor.DoctorProfileRepository;
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
    private final DoctorProfileRepository doctorProfileRepository;

    public SessionDelayService(
            SessionRepository sessionRepository,
            SlotRepository slotRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            DoctorProfileRepository doctorProfileRepository) {
        this.sessionRepository = sessionRepository;
        this.slotRepository = slotRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.doctorProfileRepository = doctorProfileRepository;
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
     */
    @Transactional(readOnly = true)
    public SessionDelay currentDelay(UUID callerAccountId, UUID clinicId, UUID sessionId) {
        Session session = resolveAuthorizedSession(callerAccountId, clinicId, sessionId);
        if (session.getMode() != ScheduleMode.FIXED_TIME) {
            return new SessionDelay(false, null);
        }
        return new SessionDelay(true, session.getDelayMinutes());
    }

    /**
     * 061-doctor-live-status (research.md Decision 3): the clinic-scoping + doctor-self-scoping
     * check shared by {@link #currentDelay} and {@link SessionLiveStatusService} - extracted
     * verbatim from {@code currentDelay}'s own prior body, no behavior change.
     *
     * <p>_diagnostics [CRITICAL] - [full-repo-audit] - [CROSS_CLINIC_LEAK]: any active role at
     * {@code clinicId} - "staff or the doctor may both view" (FR-006) means no restriction on
     * WHICH role, not no restriction on WHICH clinic.
     *
     * <p>doctor-console-cross-doctor-leak fix: also reuses SessionDaySheetController's doctor
     * self-scoping - a caller whose only active role at this clinic is Doctor gets a 404 for a
     * session belonging to a different doctor, exactly as SessionDaySheetController already
     * does for the same Session concept, instead of reading back another doctor's delay figure.
     */
    Session resolveAuthorizedSession(UUID callerAccountId, UUID clinicId, UUID sessionId) {
        List<RoleAssignment> roles =
                roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId);
        if (roles.isEmpty()) {
            throw new NotStaffedAtClinicException();
        }
        boolean doctorOnly = roles.stream().noneMatch(ra -> ra.getRole() != RoleAssignment.Role.Doctor);

        Session session = sessionRepository
                .findById(sessionId)
                .filter(s -> s.getClinic().getId().equals(clinicId))
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
        if (doctorOnly) {
            UUID myDoctorProfileId = doctorProfileRepository
                    .findByAccount_Id(callerAccountId)
                    .map(dp -> dp.getId())
                    .orElse(null);
            if (!session.getDoctorProfile().getId().equals(myDoctorProfileId)) {
                throw new SessionNotFoundException(sessionId);
            }
        }
        return session;
    }

    public record SessionDelay(boolean applicable, Integer delayMinutes) {}
}
