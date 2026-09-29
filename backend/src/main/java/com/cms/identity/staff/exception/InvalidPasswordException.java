package com.cms.identity.staff.exception;

import java.util.List;

/** real-bug-fix 2026-09-17: StaffPasswordResetService.setPassword's own password-policy check, mirroring com.cms.identity.admin's identical exception. */
public class InvalidPasswordException extends RuntimeException {

    private final List<String> failedRules;

    public InvalidPasswordException(List<String> failedRules) {
        super("Password does not satisfy the required policy");
        this.failedRules = failedRules;
    }

    public List<String> getFailedRules() {
        return failedRules;
    }
}
