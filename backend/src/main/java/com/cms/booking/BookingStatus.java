package com.cms.booking;

public enum BookingStatus {
    ACTIVE,
    /** 028-individual-booking-cancellation: reachable only from ACTIVE - a terminal, one-way transition. */
    CANCELLED
}
