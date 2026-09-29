package com.cms.protection.exception;

import java.util.UUID;

/** 060-booking-abuse-prevention FR-023: an already-resolved flag cannot be resolved again. */
public class FlagAlreadyResolvedException extends RuntimeException {

    public FlagAlreadyResolvedException(UUID flagId) {
        super("Flag " + flagId + " is already resolved");
    }
}
