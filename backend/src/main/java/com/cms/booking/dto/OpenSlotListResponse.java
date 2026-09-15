package com.cms.booking.dto;

import java.util.List;

/**
 * pagination-unification-2026-09-10: wraps what used to be a raw {@code List<OpenSlotResponse>}
 * response body so the endpoint can carry page metadata - a clinic's open-slot inventory across
 * every Fixed-Time doctor is unbounded without a doctor filter.
 */
public record OpenSlotListResponse(List<OpenSlotResponse> slots, int page, int pageSize, long totalCount) {}
