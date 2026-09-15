package com.cms.scheduling;

import java.util.UUID;

/** 026 FR-001: the Slot's status is not BOOKED - either still OPEN (nothing to complete) or already COMPLETED (one-way transition). */
public class SlotNotCompletableException extends RuntimeException {

    public SlotNotCompletableException(UUID slotId) {
        super("Slot " + slotId + " is not in a completable (BOOKED) state");
    }
}
