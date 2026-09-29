package com.cms.booking.exception;

/** 063-front-desk-walk-in (contract section 1): the patient is already waiting in, or booked into, this session; staff must confirm (FR-017). */
public class DuplicateWalkInException extends RuntimeException {

    public DuplicateWalkInException() {
        super("This patient is already in this session today. Register them again?");
    }
}
