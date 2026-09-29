package com.cms.protection.domain;

/** 060-booking-abuse-prevention (spec.md FR-016-FR-020): the five admin-flagging signals. */
public enum SuspiciousActivitySignalType {
    HIGH_ATTEMPT_VOLUME,
    REPEATED_CANCELLATIONS,
    REPEATED_NO_SHOWS,
    OVERLAPPING_APPOINTMENTS,
    REPEATED_RATE_LIMIT_VIOLATIONS
}
