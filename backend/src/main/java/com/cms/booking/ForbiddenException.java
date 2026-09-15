package com.cms.booking;

/** 017 FR-006: the caller is neither the named doctor nor an active ClinicAdmin at any clinic that doctor is actively staffed at. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException() {
        super("Not authorized to manage this doctor's appointment types or default fee");
    }
}
