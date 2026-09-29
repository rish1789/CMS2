package com.cms.booking.exception;



import java.util.UUID;

public class SlotNotFoundException extends RuntimeException {

    public SlotNotFoundException(UUID slotId) {
        super("No Slot with id " + slotId);
    }
}
