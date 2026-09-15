package com.cms.booking;

import java.util.UUID;

/** 020 FR-006: the Slot is not OPEN - either already booked, or a lost concurrent race against the database's own uniqueness guarantee. */
public class SlotAlreadyBookedException extends RuntimeException {

    public SlotAlreadyBookedException(UUID slotId) {
        super("Slot " + slotId + " is already booked");
    }
}
