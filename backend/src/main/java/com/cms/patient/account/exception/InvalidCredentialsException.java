package com.cms.patient.account.exception;

/**
 * 075-login-hardening (D-3C-2): the one login failure for an unknown identifier and for a wrong
 * password alike - it replaces the earlier ACCOUNT_NOT_FOUND / INCORRECT_PASSWORD split, which let
 * anyone test whether an email was registered.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
