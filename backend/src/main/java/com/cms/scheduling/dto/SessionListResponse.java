package com.cms.scheduling.dto;

import java.util.List;
import java.util.UUID;

/** 042-day-sheet-hardening FR-004/005: {@code doctors} is always the complete set for the
 * current window/self-scope, independent of {@code page}/the requested doctor filter - see
 * SessionRepository.findDistinctDoctorsInWindow. */
public record SessionListResponse(
        List<SessionSummaryResponse> sessions,
        List<DoctorSummary> doctors,
        int page,
        int pageSize,
        long totalCount) {

    public record DoctorSummary(UUID doctorProfileId, String name, String staffCode) {}
}
