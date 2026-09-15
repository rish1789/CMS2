package com.cms.identity.admin;

/**
 * Super Admin console redesign: rejection only ever applies to a Pending registration - an
 * already-verified clinic or doctor must be un-verified/revoked instead, which is a different
 * action with different consequences (008-deverification-cascade).
 */
public class CannotRejectVerifiedException extends RuntimeException {

    public CannotRejectVerifiedException(String message) {
        super(message);
    }
}
