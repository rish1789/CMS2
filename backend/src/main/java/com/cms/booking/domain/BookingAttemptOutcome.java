package com.cms.booking.domain;

/** 060-booking-abuse-prevention: the outcome recorded on every {@link BookingAttemptLog} row. */
public enum BookingAttemptOutcome {
    SUCCESS,
    RATE_LIMITED,
    LIMIT_REACHED,
    OTHER_FAILURE
}
