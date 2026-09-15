package com.cms.patient.record;

/** 041-staff-console-pickers: {@code q} missing or under 2 characters (contracts/staff-console-pickers.md). */
public class InvalidSearchTermException extends RuntimeException {

    public InvalidSearchTermException() {
        super("Search term must be at least 2 characters");
    }
}
