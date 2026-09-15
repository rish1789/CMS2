package com.cms.identity.account;

/** Thrown for both an unknown email and a wrong password - identical response either way (FR-007-style no-leak). */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
