package com.cms.waitlist.exception;



/** FR-001..FR-003: neither doctorProfileId nor specialization was given (or both were). */
public class WaitlistTargetRequiredException extends RuntimeException {

    public WaitlistTargetRequiredException() {
        super("Exactly one of doctorProfileId or specialization is required");
    }
}
