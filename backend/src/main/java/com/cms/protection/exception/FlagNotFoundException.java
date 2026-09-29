package com.cms.protection.exception;

import java.util.UUID;

/** 060-booking-abuse-prevention FR-022: no flag with this id, or it doesn't belong to the requesting clinic. */
public class FlagNotFoundException extends RuntimeException {

    public FlagNotFoundException(UUID flagId) {
        super("Flag " + flagId + " not found");
    }
}
