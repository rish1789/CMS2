package com.cms.clinical.exception;

/** 059-patient-clinical-record-access: the availability endpoint's `bookingIds` query param was missing or empty. */
public class BookingIdsRequiredException extends RuntimeException {

    public BookingIdsRequiredException() {
        super("bookingIds is required and must not be empty");
    }
}
