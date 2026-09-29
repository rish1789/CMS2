package com.cms.patient.account.exception;

/**
 * Thrown when a patient login email resolves to a real {@code PatientAccount} but the supplied
 * password does not match. Deliberately distinct from {@link AccountNotFoundException} - see
 * that class's Javadoc for why.
 */
public class IncorrectPasswordException extends RuntimeException {

    public IncorrectPasswordException() {
        super("Incorrect password");
    }
}
