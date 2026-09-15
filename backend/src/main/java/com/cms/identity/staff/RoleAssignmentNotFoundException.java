package com.cms.identity.staff;

/** Thrown when the target Account has no Role Assignment at the given clinic. */
public class RoleAssignmentNotFoundException extends RuntimeException {

    public RoleAssignmentNotFoundException() {
        super("No staff Role Assignment found for that account at this clinic");
    }
}
