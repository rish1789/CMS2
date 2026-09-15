package com.cms.booking;

/** Mirrors identity.clinic's and patient.account's own local copies - the same field-shape rule, each module owns its own exception. */
public class InvalidMobileNumberException extends RuntimeException {

    public InvalidMobileNumberException() {
        super("Mobile number must be a valid Indian number");
    }
}
