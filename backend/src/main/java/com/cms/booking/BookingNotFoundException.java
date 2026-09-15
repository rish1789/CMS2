package com.cms.booking;

import java.util.UUID;

/** 027: no Booking with this id, or it doesn't belong to the requesting clinic/patient. */
public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(UUID bookingId) {
        super("Booking " + bookingId + " not found");
    }
}
