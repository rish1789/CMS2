package com.cms.patient.account;

public class SignupFailedException extends RuntimeException {

    public SignupFailedException(Throwable cause) {
        super("Signup could not be completed", cause);
    }
}
