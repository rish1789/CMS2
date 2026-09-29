package com.cms.protection.domain;

/** 060-booking-abuse-prevention (spec.md FR-023): one-way transition, mirrors BookingStatus's own convention. */
public enum SuspiciousActivityFlagStatus {
    OUTSTANDING,
    RESOLVED
}
