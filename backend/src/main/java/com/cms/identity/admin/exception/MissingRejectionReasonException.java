package com.cms.identity.admin.exception;



/** Super Admin console redesign: a rejection reason is mandatory whenever a clinic or doctor profile is rejected. */
public class MissingRejectionReasonException extends RuntimeException {

    public MissingRejectionReasonException() {
        super("A rejection reason is required");
    }
}
