package com.cms.scheduling.api;

import com.cms.scheduling.domain.Session;
import com.cms.scheduling.exception.NotStaffedAtClinicException;
import com.cms.scheduling.repository.SessionCancellationRepository;
import com.cms.scheduling.repository.SessionRepository;
import com.cms.scheduling.repository.SlotRepository;


import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.account.config.SecurityConfig;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.scheduling.dto.SessionListResponse;
import com.cms.scheduling.dto.SessionListResponse.DoctorSummary;
import com.cms.scheduling.dto.SessionSummaryResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 041-staff-console-pickers FR-003/FR-008: the day sheet's top-level session list, bounded to
 * a fixed 14-day window (today through today+14) - no arbitrary date range in v1. Authorization
 * is the same "any active role at this clinic" gate every other permissive staff-read endpoint
 * in this codebase already uses (e.g. Inbox, queue position).
 *
 * <p>Doctor self-scoping follow-up: a caller whose *only* active role at this clinic is Doctor
 * sees only their own sessions here (view-only visibility scoping, no change to any existing
 * write-action authorization rule). A caller who also holds ClinicAdmin or Operations at this
 * clinic keeps seeing every doctor's sessions, unchanged.
 *
 * <p>042-day-sheet-hardening FR-004/005/006: paginated (default page 0, size 20) and optionally
 * filtered to one doctor via {@code doctorProfileId} - both independent of, and ANDed with, the
 * Doctor self-scoping restriction above (see SessionRepository.findByClinicAndWindow).
 */
@RestController
@RequestMapping("/api/v1/clinics/{clinicId}/sessions")
public class ClinicSessionListController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final SessionRepository sessionRepository;
    private final SlotRepository slotRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final SessionCancellationRepository sessionCancellationRepository;

    public ClinicSessionListController(
            SessionRepository sessionRepository,
            SlotRepository slotRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            DoctorProfileRepository doctorProfileRepository,
            SessionCancellationRepository sessionCancellationRepository) {
        this.sessionRepository = sessionRepository;
        this.slotRepository = slotRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.doctorProfileRepository = doctorProfileRepository;
        this.sessionCancellationRepository = sessionCancellationRepository;
    }

    @GetMapping
    public SessionListResponse list(
            @PathVariable UUID clinicId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) UUID doctorProfileId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        List<RoleAssignment> roles =
                roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId);
        if (roles.isEmpty()) {
            throw new NotStaffedAtClinicException();
        }
        boolean doctorOnly = roles.stream().noneMatch(ra -> ra.getRole() != RoleAssignment.Role.Doctor);

        LocalDate windowStart = from != null ? from : LocalDate.now();
        LocalDate windowEnd = to != null ? to : windowStart.plusDays(14);

        UUID selfScopeDoctorProfileId = null;
        if (doctorOnly) {
            selfScopeDoctorProfileId = doctorProfileRepository
                    .findByAccount_Id(callerAccountId)
                    .map(dp -> dp.getId())
                    .orElse(null);
            if (selfScopeDoctorProfileId == null) {
                // Doctor-only role with no resolvable DoctorProfile - an inconsistent state that
                // shouldn't occur in practice; fail closed (show nothing) rather than accidentally
                // dropping the self-scope restriction and showing every doctor's sessions.
                return new SessionListResponse(List.of(), List.of(), page, size, 0);
            }
        }

        Page<Session> sessionPage = sessionRepository.findByClinicAndWindow(
                clinicId, windowStart, windowEnd, selfScopeDoctorProfileId, doctorProfileId,
                PageRequest.of(page, size));

        var doctors = sessionRepository
                .findDistinctDoctorsInWindow(clinicId, windowStart, windowEnd, selfScopeDoctorProfileId)
                .stream()
                .map(d -> new DoctorSummary(d.getDoctorProfileId(), d.getName(), d.getStaffCode()))
                .toList();

        // 042-day-sheet-hardening FR-011: one bulk query for every session on this page's
        // booked/total slot counts, instead of one query per session (research.md R3).
        List<UUID> pageSessionIds = sessionPage.getContent().stream().map(Session::getId).toList();
        Map<UUID, SlotRepository.SlotCountBySession> countsBySessionId =
                slotRepository.countBySessionIdIn(pageSessionIds).stream()
                        .collect(Collectors.toMap(SlotRepository.SlotCountBySession::getSessionId, Function.identity()));

        // 063-front-desk-walk-in (research.md Decision 6): the same one-bulk-query shape for the
        // walk-in line counts and the Doctor free/busy hint.
        Map<UUID, SlotRepository.WalkInLineBySession> walkInsBySessionId =
                slotRepository.countWalkInLineBySessionIdIn(pageSessionIds).stream()
                        .collect(Collectors.toMap(SlotRepository.WalkInLineBySession::getSessionId, Function.identity()));

        // 065-phase1-stabilization (owner decision 3): whole-cancelled sessions, same one-query shape.
        Set<UUID> wholeCancelledIds =
                Set.copyOf(sessionCancellationRepository.findWholeCancelledSessionIdsIn(pageSessionIds));

        var sessions = sessionPage.getContent().stream()
                .map(session -> {
                    SlotRepository.SlotCountBySession counts = countsBySessionId.get(session.getId());
                    SlotRepository.WalkInLineBySession walkIns = walkInsBySessionId.get(session.getId());
                    return SessionSummaryResponse.from(
                            session,
                            counts == null ? null : counts.getBookedSlots(),
                            counts == null ? null : counts.getTotalSlots(),
                            walkIns == null ? 0 : walkIns.getWalkInsWaiting(),
                            walkIns != null && walkIns.getAppearedCount() > 0,
                            wholeCancelledIds.contains(session.getId()));
                })
                .toList();
        return new SessionListResponse(sessions, doctors, page, size, sessionPage.getTotalElements());
    }
}
