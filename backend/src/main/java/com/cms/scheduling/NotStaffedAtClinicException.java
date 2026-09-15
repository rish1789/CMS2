package com.cms.scheduling;

/**
 * 041-staff-console-pickers: the caller has no active role at the target clinic. Deliberately
 * NOT a reuse of {@link ForbiddenException} (013's own "manage schedules" message) - this
 * module's own diagnostics history (see {@code com.cms.patient.record.ForbiddenException}'s
 * Javadoc) already documents why reusing a differently-worded exception across unrelated
 * actions leaks a wrong message to the caller.
 */
public class NotStaffedAtClinicException extends RuntimeException {

    public NotStaffedAtClinicException() {
        super("Not an active staff member at this clinic");
    }
}
