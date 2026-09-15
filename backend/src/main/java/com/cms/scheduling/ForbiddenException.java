package com.cms.scheduling;

/** 013 FR-001..FR-003: the caller is neither an active ClinicAdmin at the named clinic nor the named doctor themselves. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException() {
        super("Not authorized to manage schedules for this doctor at this clinic");
    }
}
