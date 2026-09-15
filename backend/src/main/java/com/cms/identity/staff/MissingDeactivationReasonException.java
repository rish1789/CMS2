package com.cms.identity.staff;

/** Employee deactivation modal: a reason is mandatory whenever an active Role Assignment is deactivated. */
public class MissingDeactivationReasonException extends RuntimeException {

    public MissingDeactivationReasonException() {
        super("A deactivation reason is required");
    }
}
