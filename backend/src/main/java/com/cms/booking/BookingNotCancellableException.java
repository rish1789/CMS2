package com.cms.booking;

import java.util.UUID;

/** 028 FR-003: the Booking is already CANCELLED, or its Slot is not BOOKED (already NO_SHOW/COMPLETED - a resolved outcome). */
public class BookingNotCancellableException extends RuntimeException {

    public BookingNotCancellableException(UUID bookingId) {
        super("Booking " + bookingId + " is not in a cancellable state");
    }
}
