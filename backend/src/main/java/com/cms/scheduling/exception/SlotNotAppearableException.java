package com.cms.scheduling.exception;

import java.util.UUID;

/** 057-day-sheet-status-overhaul: the Slot's status is neither BOOKED nor NO_SHOW - the only two source states markAppeared accepts. */
public class SlotNotAppearableException extends RuntimeException {

    public SlotNotAppearableException(UUID slotId) {
        super("Slot " + slotId + " is not in an appearable (BOOKED or NO_SHOW) state");
    }
}
