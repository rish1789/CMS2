package com.cms.booking.dto;

import java.util.List;
import java.util.UUID;

/**
 * 057-day-sheet-status-overhaul FR-014: a 200 is returned even when some (or all) individual
 * bookings failed - the caller reports the specific per-booking failure rather than rejecting
 * the whole batch.
 */
public record BatchCancelResponse(List<UUID> cancelled, List<Failure> failed) {

    public record Failure(UUID bookingId, String reason) {}
}
