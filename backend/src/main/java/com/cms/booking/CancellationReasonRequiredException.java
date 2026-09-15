package com.cms.booking;

import java.util.UUID;

/** patient-cancellation-reason: a patient self-service cancellation with no reason at all - staff cancellations never require one. */
public class CancellationReasonRequiredException extends RuntimeException {

    public CancellationReasonRequiredException(UUID bookingId) {
        super("A cancellation reason is required to cancel booking " + bookingId);
    }
}
