package com.cms.identity.admin;

/** Super Admin console redesign: the {@code status} list query param isn't PENDING, VERIFIED, or REJECTED. */
public class InvalidListStatusException extends RuntimeException {

    public InvalidListStatusException(String status) {
        super("status must be one of PENDING, VERIFIED, REJECTED (got '" + status + "')");
    }
}
