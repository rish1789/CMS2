package com.cms.common.login;

/** 075-login-hardening: too many failed logins for this identifier - the same for real and unregistered ones. */
public class LoginTemporarilyLockedException extends RuntimeException {

    private final long retryAfterSeconds;

    public LoginTemporarilyLockedException(long retryAfterSeconds) {
        super("Too many failed sign-in attempts. Please wait and try again.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
