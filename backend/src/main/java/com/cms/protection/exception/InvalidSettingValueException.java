package com.cms.protection.exception;

/** 060-booking-abuse-prevention FR-026: a value that fails its setting's own type/range validation (data-model.md). */
public class InvalidSettingValueException extends RuntimeException {

    public InvalidSettingValueException(String name, String value) {
        super("Invalid value \"" + value + "\" for setting " + name);
    }
}
