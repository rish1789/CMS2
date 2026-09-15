package com.cms.inbox;

import java.util.UUID;

/** 038 FR-010/FR-011: a release/resolve attempt by someone other than the current claimant, or against an item that isn't CLAIMED. */
public class NotClaimantException extends RuntimeException {

    public NotClaimantException(UUID itemId) {
        super("Caller is not the current claimant of Inbox Item " + itemId);
    }
}
