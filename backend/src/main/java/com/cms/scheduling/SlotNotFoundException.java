package com.cms.scheduling;

import java.util.UUID;

/** 026: no Slot with this id at this clinic - distinct from com.cms.booking.SlotNotFoundException (016), a different module's own exception of the same name. */
public class SlotNotFoundException extends RuntimeException {

    public SlotNotFoundException(UUID slotId) {
        super("Slot " + slotId + " not found");
    }
}
