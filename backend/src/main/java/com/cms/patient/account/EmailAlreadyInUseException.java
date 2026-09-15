package com.cms.patient.account;

public class EmailAlreadyInUseException extends RuntimeException {

    public EmailAlreadyInUseException() {
        super("This email is already registered to a Patient Account");
    }
}
