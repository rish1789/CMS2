package com.cms.booking.exception;

/** 063-front-desk-walk-in (contract section 1): an OTHER visit reason needs free text of at most 200 characters (FR-004). */
public class VisitReasonDetailRequiredException extends RuntimeException {

    public VisitReasonDetailRequiredException() {
        super("Describe the reason for the visit (up to 200 characters).");
    }
}
