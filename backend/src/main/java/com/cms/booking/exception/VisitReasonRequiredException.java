package com.cms.booking.exception;

/** 063-front-desk-walk-in (contract section 1): the visit reason is missing or not one of the listed reasons (FR-004). */
public class VisitReasonRequiredException extends RuntimeException {

    public VisitReasonRequiredException() {
        super("Choose why the patient came in.");
    }
}
