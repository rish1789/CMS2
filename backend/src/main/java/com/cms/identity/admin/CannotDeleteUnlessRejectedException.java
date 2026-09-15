package com.cms.identity.admin;

/** Super Admin console redesign: permanent delete only ever applies to an already-rejected registration - defense in depth behind the UI, which only ever shows Delete on the Rejected tab. */
public class CannotDeleteUnlessRejectedException extends RuntimeException {

    public CannotDeleteUnlessRejectedException() {
        super("Only a rejected registration can be permanently deleted");
    }
}
