package com.cms.scheduling.dto;

import com.cms.scheduling.domain.Session;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * 041-staff-console-pickers FR-003: one row per Session in the day sheet's session list.
 *
 * <p>042-day-sheet-hardening FR-011: bookedSlotCount/totalSlotCount are 0/0 for a session with
 * no generated slots yet (the caller passes null when the bulk-loaded count has no matching row
 * for this session, since GROUP BY produces no row at all for a session with zero slots) - the
 * frontend renders that as an explicit "no slots yet" state, not a "0 of 0" fraction.
 *
 * <p>staff-console-audit-2026-09-10 P1: startTime/endTime added - the list previously showed
 * only the session's date, never its time range, off Session's own already-loaded fields (zero
 * new queries).
 *
 * <p>065-phase1-stabilization (owner decision 3): {@code cancelled} flags a whole-cancelled session.
 */
public record SessionSummaryResponse(
        UUID sessionId,
        UUID doctorProfileId,
        String doctorName,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        String mode,
        int bookedSlotCount,
        int totalSlotCount,
        // 063-front-desk-walk-in (research.md Decision 6): walk-ins waiting in a Fixed-Time session's
        // walk-in line, and whether anyone is in with the doctor right now (the Doctor free/busy
        // hint). 064-queue-send-in-complete: for a Queue session, the waiting tokens and whether a
        // token has been sent in.
        int walkInsWaiting,
        boolean inWithDoctor,
        boolean cancelled) {

    public static SessionSummaryResponse from(Session session, Long bookedSlotCount, Long totalSlotCount) {
        return from(session, bookedSlotCount, totalSlotCount, 0, false, false);
    }

    public static SessionSummaryResponse from(
            Session session,
            Long bookedSlotCount,
            Long totalSlotCount,
            long walkInsWaiting,
            boolean inWithDoctor,
            boolean cancelled) {
        return new SessionSummaryResponse(
                session.getId(),
                session.getDoctorProfile().getId(),
                session.getDoctorProfile().getAccount().getName(),
                session.getSessionDate(),
                session.getStartTime(),
                session.getEndTime(),
                session.getMode().name(),
                bookedSlotCount == null ? 0 : bookedSlotCount.intValue(),
                totalSlotCount == null ? 0 : totalSlotCount.intValue(),
                (int) walkInsWaiting,
                inWithDoctor,
                cancelled);
    }
}
