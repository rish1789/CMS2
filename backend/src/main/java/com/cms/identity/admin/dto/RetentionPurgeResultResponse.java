package com.cms.identity.admin.dto;

/** contracts/retention-purge.md: the count of bookings whose clinical content was purged by this run. */
public record RetentionPurgeResultResponse(int purgedBookingCount) {}
