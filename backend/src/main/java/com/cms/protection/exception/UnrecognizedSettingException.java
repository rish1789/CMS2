package com.cms.protection.exception;

/** 060-booking-abuse-prevention FR-026: a write to a setting name outside the fixed, known set (data-model.md). */
public class UnrecognizedSettingException extends RuntimeException {

    public UnrecognizedSettingException(String name) {
        super("Unrecognized setting name: " + name);
    }
}
