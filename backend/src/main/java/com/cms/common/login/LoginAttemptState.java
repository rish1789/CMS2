package com.cms.common.login;

import java.time.Duration;
import java.time.Instant;

/** 075-login-hardening: one identifier's failed-login record - pure arithmetic, no I/O. */
public record LoginAttemptState(int failedCount, Instant windowStartedAt, Instant lockedUntil) {

    public static LoginAttemptState none() {
        return new LoginAttemptState(0, null, null);
    }

    public boolean lockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Whole seconds until the lock ends, rounded up - the {@code Retry-After} value. */
    public long retryAfterSeconds(Instant now) {
        long millis = Duration.between(now, lockedUntil).toMillis();
        return Math.max(1, (millis + 999) / 1000);
    }

    /**
     * The record after one more failure: a new count starts when there is none, when the window
     * has passed, or when an earlier lock has expired; reaching the threshold sets the lock.
     */
    public LoginAttemptState afterFailure(Instant now, LoginAttemptPolicy policy) {
        boolean fresh = windowStartedAt == null
                || !now.isBefore(windowStartedAt.plus(policy.window()))
                || (lockedUntil != null && !lockedUntil.isAfter(now));
        int count = fresh ? 1 : failedCount + 1;
        Instant start = fresh ? now : windowStartedAt;
        Instant lock = count >= policy.maxFailures() ? now.plus(policy.lockDuration()) : null;
        return new LoginAttemptState(count, start, lock);
    }
}
