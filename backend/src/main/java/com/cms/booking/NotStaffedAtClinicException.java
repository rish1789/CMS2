package com.cms.booking;

/**
 * 041-staff-console-pickers: the caller has no active role at the target clinic. Deliberately
 * NOT a reuse of {@link ForbiddenException} (017's own "appointment types or default fee"
 * message) - this codebase's own diagnostics history (see {@code
 * com.cms.patient.record.ForbiddenException}'s Javadoc) already documents why reusing a
 * differently-worded exception across unrelated actions leaks a wrong message to the caller.
 */
public class NotStaffedAtClinicException extends RuntimeException {

    public NotStaffedAtClinicException() {
        super("Not an active staff member at this clinic");
    }
}
