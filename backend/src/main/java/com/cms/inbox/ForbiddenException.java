package com.cms.inbox;

/** 038 research.md R6: caller is authenticated but not an active Operations/ClinicAdmin at this clinic. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException() {
        super("Caller is not an active Operations or ClinicAdmin at this clinic");
    }
}
