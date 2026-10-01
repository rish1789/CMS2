package com.cms.identity.account.exception;

/**
 * 075-login-hardening (D-3C-1): the password was right, but the account holds no active role at any
 * clinic - every one was deactivated. Shown only after a correct password, so it reveals nothing to
 * someone guessing.
 */
public class NoActiveClinicAccessException extends RuntimeException {

    public NoActiveClinicAccessException() {
        super("This account has no active clinic access.");
    }
}
