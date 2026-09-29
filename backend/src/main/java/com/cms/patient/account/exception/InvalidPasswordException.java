package com.cms.patient.account.exception;



import java.util.List;

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
