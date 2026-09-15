package com.cms.identity.admin;

/** Super Admin console redesign: the submitted reason isn't one of the recognized rejection reason codes. */
public class InvalidRejectionReasonException extends RuntimeException {

    public InvalidRejectionReasonException() {
        super("Rejection reason must be one of DUPLICATE_REGISTRATION, SUSPECTED_FRAUD, INVALID_DETAILS, "
                + "UNREACHABLE_CONTACT, OTHER");
    }
}
