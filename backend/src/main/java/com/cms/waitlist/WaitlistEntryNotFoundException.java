package com.cms.waitlist;

import java.util.UUID;

/** 032 FR-003/research.md R8: no entry with this id, or it doesn't belong to the caller - never distinguished, to avoid confirming existence. */
public class WaitlistEntryNotFoundException extends RuntimeException {

    public WaitlistEntryNotFoundException(UUID entryId) {
        super("No Waitlist Entry with id " + entryId);
    }
}
