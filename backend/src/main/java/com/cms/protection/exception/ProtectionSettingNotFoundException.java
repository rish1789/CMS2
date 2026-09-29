package com.cms.protection.exception;

/** 060-booking-abuse-prevention: a history lookup for a setting name outside the fixed, known set (data-model.md) - distinct from UnrecognizedSettingException's 400 on the write path, since contracts.md gives this read path a 404 instead. */
public class ProtectionSettingNotFoundException extends RuntimeException {

    public ProtectionSettingNotFoundException(String name) {
        super("Unrecognized setting name: " + name);
    }
}
