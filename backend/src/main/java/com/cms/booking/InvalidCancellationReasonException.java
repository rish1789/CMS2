package com.cms.booking;

/** patient-cancellation-reason: the {@code reason} value didn't match any {@link BookingCancellationReason} literal. */
public class InvalidCancellationReasonException extends RuntimeException {

    public InvalidCancellationReasonException(String reason) {
        super("'" + reason + "' is not a recognized cancellation reason");
    }
}
