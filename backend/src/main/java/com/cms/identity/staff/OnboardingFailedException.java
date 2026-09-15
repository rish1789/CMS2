package com.cms.identity.staff;

/** Wraps any unexpected failure during the atomic onboarding transaction (FR-009), mirroring RegistrationFailedException (001). */
public class OnboardingFailedException extends RuntimeException {

    public OnboardingFailedException(Throwable cause) {
        super("Onboarding failed", cause);
    }
}
