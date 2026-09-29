package com.cms.booking.exception;

/** 063-front-desk-walk-in (contract section 1): a new patient email was given but is not a valid address (FR-002). */
public class InvalidEmailException extends RuntimeException {

    public InvalidEmailException() {
        super("Enter a valid email address, or leave it blank.");
    }
}
