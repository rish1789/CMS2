package com.cms.booking;

import java.util.UUID;

/** 028 FR-002: a patient's self-service cancellation attempt was made less than 2 hours before the scheduled slot time - only staff can cancel it now. */
public class CancellationCutoffPassedException extends RuntimeException {

    public CancellationCutoffPassedException(UUID bookingId) {
        super("Booking " + bookingId
                + " is less than 2 hours from its scheduled slot time - please contact the clinic to cancel");
    }
}
