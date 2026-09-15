package com.cms.booking;

import java.util.UUID;

/** 025 FR-009: no Slot qualifies at any priority tier (buffer, no-show-freed, or regular OPEN) for this Session. */
public class NoSlotAvailableException extends RuntimeException {

    public NoSlotAvailableException(UUID sessionId) {
        super("No eligible Slot available for walk-in insertion in Session " + sessionId);
    }
}
