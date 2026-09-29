package com.cms.booking.exception;

import java.util.UUID;

/**
 * 063-front-desk-walk-in (contract section 4): a walk-in has no scheduled time for the patient
 * self-service cutoff to apply to, and is removed from the walk-in line by staff instead (FR-015).
 */
public class WalkInNotSelfCancellableException extends RuntimeException {

    public WalkInNotSelfCancellableException(UUID bookingId) {
        super("Walk-in visits are removed by clinic staff, not cancelled online.");
    }
}
