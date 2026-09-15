package com.cms.identity.staff;

/** Employee deactivation modal: the submitted reason isn't one of RoleAssignment.DeactivationReason's values. */
public class InvalidDeactivationReasonException extends RuntimeException {

    public InvalidDeactivationReasonException() {
        super("Deactivation reason must be RESIGNED or SERVICE_NOT_REQUIRED");
    }
}
