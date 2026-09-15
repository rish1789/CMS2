package com.cms.inbox.dto;

import com.cms.inbox.InboxItem;
import com.cms.inbox.InboxItemStatus;
import com.cms.inbox.InboxItemType;
import java.time.Instant;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * contracts/inbox.md: {@code summary}'s shape is {@code itemType}-dependent, assembled here from
 * the live {@code booking}/{@code waitlistEntry} relation (never a frozen copy - research.md R4),
 * so 033's anonymization/034's purge are reflected automatically (spec FR-016).
 */
public record InboxItemResponse(
        UUID id,
        InboxItemType itemType,
        InboxItemStatus status,
        UUID claimedByAccountId,
        String claimedByName,
        Instant createdAt,
        Map<String, Object> summary) {

    public static InboxItemResponse of(InboxItem item, String claimedByNameOrNull) {
        Map<String, Object> summary =
                switch (item.getItemType()) {
                    case WALK_IN -> walkInSummary(item);
                    case WAITLIST_OFFER -> waitlistOfferSummary(item);
                    case DEVERIFICATION_CASCADE -> cascadeSummary(item);
                };
        return new InboxItemResponse(
                item.getId(),
                item.getItemType(),
                item.getStatus(),
                item.getClaimedByAccountId(),
                claimedByNameOrNull,
                item.getCreatedAt(),
                summary);
    }

    private static Map<String, Object> walkInSummary(InboxItem item) {
        LocalTime startTime = item.getBooking().getSlot().getStartTime();
        return Map.of(
                "bookingId", item.getBooking().getId(),
                "patientName", item.getBooking().getPatient().getName(),
                "slotStartTime", startTime == null ? "" : startTime.toString());
    }

    private static Map<String, Object> waitlistOfferSummary(InboxItem item) {
        return Map.of(
                "waitlistEntryId", item.getWaitlistEntry().getId(),
                "patientContact", item.getWaitlistEntry().getPatientAccount().getEmail(),
                "offerExpiresAt", String.valueOf(item.getWaitlistEntry().getOfferExpiresAt()));
    }

    private static Map<String, Object> cascadeSummary(InboxItem item) {
        // 038 FR-004: doctorName is null for a clinic-wide de-verification cascade (no single
        // doctor identifies it) - Map.of() rejects null values, so build explicitly.
        Map<String, Object> summary = new HashMap<>();
        summary.put("doctorName", item.getDoctorName());
        summary.put("cancelledBookingCount", item.getCancelledBookingCount());
        return summary;
    }
}
