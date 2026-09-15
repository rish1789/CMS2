package com.cms.patient.account;

/**
 * Thrown for both an unregistered email and a wrong password (FR-007) - the caller
 * MUST NOT distinguish between the two cases in the response, so a single exception
 * type with a single fixed message is deliberate, not an oversight.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
