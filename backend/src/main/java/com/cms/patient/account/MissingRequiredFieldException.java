package com.cms.patient.account;

public class MissingRequiredFieldException extends RuntimeException {

    private final String field;

    public MissingRequiredFieldException(String field) {
        super("Required field is missing: " + field);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
