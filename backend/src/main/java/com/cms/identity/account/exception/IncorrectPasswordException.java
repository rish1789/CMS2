package com.cms.identity.account.exception;

/**
 * Thrown when a staff login identifier resolves to a real {@code Account} (or the configured
 * Super Admin identity) but the supplied password does not match. Deliberately distinct from
 * {@link AccountNotFoundException} - see that class's Javadoc for why.
 */
public class IncorrectPasswordException extends RuntimeException {

    public IncorrectPasswordException() {
        super("Incorrect password");
    }
}
