package com.cms.identity.admin.exception;

import java.util.List;

/** real-bug-fix 2026-09-17: setClinicAdminPassword's own password-policy check. */
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
