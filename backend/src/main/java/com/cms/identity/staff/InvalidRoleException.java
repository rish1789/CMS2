package com.cms.identity.staff;

/** Thrown when the requested onboarding role is anything other than Doctor/Operations (FR-003) - always 400, never 403. */
public class InvalidRoleException extends RuntimeException {

    public InvalidRoleException(String role) {
        super("Role must be Doctor or Operations, got: " + role);
    }
}
