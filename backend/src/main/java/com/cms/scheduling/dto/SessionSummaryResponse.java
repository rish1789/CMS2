package com.cms.scheduling.dto;

import com.cms.scheduling.Session;
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
        int totalSlotCount) {

    public static SessionSummaryResponse from(Session session, Long bookedSlotCount, Long totalSlotCount) {
        return new SessionSummaryResponse(
                session.getId(),
                session.getDoctorProfile().getId(),
                session.getDoctorProfile().getAccount().getName(),
                session.getSessionDate(),
                session.getStartTime(),
                session.getEndTime(),
                session.getMode().name(),
                bookedSlotCount == null ? 0 : bookedSlotCount.intValue(),
                totalSlotCount == null ? 0 : totalSlotCount.intValue());
    }
}
