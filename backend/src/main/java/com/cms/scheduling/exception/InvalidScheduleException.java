package com.cms.scheduling.exception;



/** 013 FR-005..FR-008: a single, shared 400 for any request-shape violation of the schedule's own field rules. */
public class InvalidScheduleException extends RuntimeException {

    public InvalidScheduleException(String message) {
        super(message);
    }
}
