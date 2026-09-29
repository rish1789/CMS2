package com.cms.identity.account.exception;

/**
 * 062-rejected-clinic-gating (FR-007): the staff member's access runs only through a clinic the
 * Super Admin has rejected, and they are not its ClinicAdmin. Mapped to 403 CLINIC_NOT_ACTIVE -
 * both at sign-in and on any clinic-scoped request to that clinic.
 */
public class StaffClinicNotActiveException extends RuntimeException {

    public static final String MESSAGE = "Your clinic is not currently active. Contact your clinic administrator.";

    public StaffClinicNotActiveException() {
        super(MESSAGE);
    }
}
