package com.cms.patient.account.exception;

/**
 * Thrown when a patient login email matches no known {@code PatientAccount}. Deliberately
 * distinct from {@link IncorrectPasswordException} - the product owner explicitly chose to
 * accept the user-enumeration tradeoff this creates in exchange for a more specific login error.
 */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException() {
        super("No account found with that email");
    }
}
