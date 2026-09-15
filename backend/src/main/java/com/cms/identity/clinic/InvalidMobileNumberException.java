package com.cms.identity.clinic;

public class InvalidMobileNumberException extends RuntimeException {

    private final String field;

    public InvalidMobileNumberException(String field) {
        super("Mobile number does not match the Indian numbering plan: " + field);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
