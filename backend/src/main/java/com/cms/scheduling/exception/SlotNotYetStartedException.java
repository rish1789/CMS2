package com.cms.scheduling.exception;

import java.util.UUID;

/**
 * 026 real-bug-fix 2026-09-16: found live - a ClinicAdmin marked a slot scheduled for 15:15
 * completed at 15:03, twelve minutes before its scheduled start even arrived. Distinct from
 * {@link SlotNotCompletableException} (wrong status) - this is a timing rule: a slot cannot be
 * marked completed before "now" reaches its own scheduled start time.
 */
public class SlotNotYetStartedException extends RuntimeException {

    public SlotNotYetStartedException(UUID slotId) {
        super("Slot " + slotId + " cannot be marked completed before its scheduled start time");
    }
}
