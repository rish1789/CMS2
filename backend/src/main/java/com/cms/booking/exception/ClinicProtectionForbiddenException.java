package com.cms.booking.exception;

/** 060-booking-abuse-prevention FR-028/FR-031: the caller does not hold ClinicAdmin at this specific clinic. */
public class ClinicProtectionForbiddenException extends RuntimeException {

    public ClinicProtectionForbiddenException() {
        super("Not authorized to manage this clinic's booking-protection settings");
    }
}
