package com.cms.protection.exception;

/** 060-booking-abuse-prevention FR-021/FR-023: the caller does not hold ClinicAdmin at this specific clinic. */
public class ProtectionForbiddenException extends RuntimeException {

    public ProtectionForbiddenException() {
        super("Not authorized to review this clinic's flagged activity");
    }
}
