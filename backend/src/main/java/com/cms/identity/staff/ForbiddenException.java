package com.cms.identity.staff;

/** Thrown when the authenticated caller is not an active ClinicAdmin for the target clinic (FR-002). */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException() {
        super("Not authorized to onboard staff for this clinic");
    }
}
