package com.cms.booking;

import java.util.UUID;

public class SlotNotFoundException extends RuntimeException {

    public SlotNotFoundException(UUID slotId) {
        super("No Slot with id " + slotId);
    }
}
