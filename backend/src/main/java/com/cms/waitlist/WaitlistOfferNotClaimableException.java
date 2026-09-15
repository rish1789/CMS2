package com.cms.waitlist;

import java.util.UUID;

/** 032 FR-004: the entry is not currently OFFERED, or its window has already lapsed. */
public class WaitlistOfferNotClaimableException extends RuntimeException {

    public WaitlistOfferNotClaimableException(UUID entryId) {
        super("Waitlist Entry " + entryId + " is not currently claimable");
    }
}
