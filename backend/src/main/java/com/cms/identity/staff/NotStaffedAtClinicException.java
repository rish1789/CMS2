package com.cms.identity.staff;

/**
 * 041-staff-console-pickers: the caller has no active role at the target clinic. Deliberately
 * NOT a reuse of {@link ForbiddenException} (004's own "onboard staff" message) - see
 * research.md R9.
 */
public class NotStaffedAtClinicException extends RuntimeException {

    public NotStaffedAtClinicException() {
        super("Not an active staff member at this clinic");
    }
}
