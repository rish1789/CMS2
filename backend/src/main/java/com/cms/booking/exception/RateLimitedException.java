package com.cms.booking.exception;

/** 060-booking-abuse-prevention FR-010/FR-011: the patient's account is currently in a rate-limit cooldown. */
public class RateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitedException(long retryAfterSeconds) {
        super("Too many booking attempts - please wait before trying again");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
