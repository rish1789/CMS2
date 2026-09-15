package com.cms.inbox;

import java.util.UUID;

/** 038 FR-008: a claim attempt against an item that is already CLAIMED or RESOLVED. */
public class AlreadyClaimedException extends RuntimeException {

    public AlreadyClaimedException(UUID itemId) {
        super("Inbox Item " + itemId + " is already claimed");
    }
}
