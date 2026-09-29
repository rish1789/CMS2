package com.cms.identity.account.exception;

/**
 * Thrown when a staff login identifier (email or staff code) matches no known {@code Account}
 * and does not match the configured Super Admin username either. Deliberately distinct from
 * {@link IncorrectPasswordException} - the product owner explicitly chose to accept the
 * user-enumeration tradeoff this creates in exchange for a more specific login error.
 */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException() {
        super("No staff account found with that email or staff code");
    }
}
