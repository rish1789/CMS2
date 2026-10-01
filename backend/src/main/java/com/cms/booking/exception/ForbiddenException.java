package com.cms.booking.exception;



/** 017 FR-006: the caller is neither the named doctor nor an active ClinicAdmin at any clinic that doctor is actively staffed at. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException() {
        super("Not authorized to manage this doctor's appointment types or default fee");
    }

    /** 068-per-clinic-fees: the same 403, with a message naming the clinic-scoped rule that refused it. */
    public ForbiddenException(String message) {
        super(message);
    }
}
