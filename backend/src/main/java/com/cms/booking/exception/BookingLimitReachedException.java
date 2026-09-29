package com.cms.booking.exception;

/** 060-booking-abuse-prevention FR-002/FR-005: the patient's active-appointment limit (global and/or per-clinic) would be exceeded. */
public class BookingLimitReachedException extends RuntimeException {

    public BookingLimitReachedException() {
        super("You've reached your current appointment limit - cancel an existing appointment to book a new one");
    }
}
