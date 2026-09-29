package com.cms.booking.exception;

/** 063-front-desk-walk-in (contract section 1): neither an existing patient nor a new patient name was given (FR-002). */
public class PatientRequiredException extends RuntimeException {

    public PatientRequiredException() {
        super("Select an existing patient or enter the new patient's name.");
    }
}
