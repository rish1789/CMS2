package com.cms.identity.clinic;

public class EmailAlreadyInUseException extends RuntimeException {

    public EmailAlreadyInUseException() {
        super("This email is already associated with an existing staff Account");
    }
}
