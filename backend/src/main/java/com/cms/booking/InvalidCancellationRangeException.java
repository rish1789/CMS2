package com.cms.booking;

/** 030 (bounded range extension): an optional {@code toTime} that isn't strictly after {@code cutoffTime}. */
public class InvalidCancellationRangeException extends RuntimeException {

    public InvalidCancellationRangeException() {
        super("The end time must be after the start time");
    }
}
