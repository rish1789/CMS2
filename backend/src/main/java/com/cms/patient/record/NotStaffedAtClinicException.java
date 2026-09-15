package com.cms.patient.record;

/**
 * 041-staff-console-pickers: the caller has no active role at the target clinic. Deliberately
 * NOT a reuse of {@link ForbiddenException} (037's own anonymize-specific message) - this
 * module's own Javadoc already documents why reusing a differently-worded exception across
 * unrelated actions leaks a wrong message to the caller (research.md R9).
 */
public class NotStaffedAtClinicException extends RuntimeException {

    public NotStaffedAtClinicException() {
        super("Not an active staff member at this clinic");
    }
}
