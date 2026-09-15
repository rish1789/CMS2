package com.cms.identity.doctor;

/**
 * 041-staff-console-pickers: the caller has no active role at the target clinic. Named
 * consistently with the sibling exceptions added to {@code scheduling}, {@code booking}, and
 * {@code patient.record} for this same feature (research.md R9) - this module has no prior
 * exception to accidentally reuse, but the name stays consistent for future readers.
 */
public class NotStaffedAtClinicException extends RuntimeException {

    public NotStaffedAtClinicException() {
        super("Not an active staff member at this clinic");
    }
}
