package com.cms.identity.clinic;

public class MissingRequiredFieldException extends RuntimeException {

    private final String field;

    public MissingRequiredFieldException(String field) {
        super("Missing required field: " + field);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
