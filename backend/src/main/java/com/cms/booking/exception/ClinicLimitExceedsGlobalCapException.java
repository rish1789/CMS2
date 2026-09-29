package com.cms.booking.exception;

/** 060-booking-abuse-prevention BR-005: a clinic's supplementary limit may only ever be equal to or stricter than the current platform-wide cap. */
public class ClinicLimitExceedsGlobalCapException extends RuntimeException {

    public ClinicLimitExceedsGlobalCapException(int requested, int globalCap) {
        super("Requested limit " + requested + " exceeds the current platform-wide cap of " + globalCap);
    }
}
