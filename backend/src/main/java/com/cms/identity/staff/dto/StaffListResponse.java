package com.cms.identity.staff.dto;

import java.util.List;

/**
 * pagination-unification-2026-09-10: {@code specializations} is always the complete set of
 * specializations among this clinic's Doctors, independent of {@code page}/the current
 * search/role/status/specialization filters - mirrors {@code SessionListResponse.doctors}'
 * identical stable-filter-options-list pattern. Without this, the specialization filter's own
 * option list would shrink to whatever happens to be on the current page, or disappear
 * entirely under an unrelated filter.
 */
public record StaffListResponse(
        List<StaffSummaryResponse> staff, int page, int pageSize, long totalCount, List<String> specializations) {}
