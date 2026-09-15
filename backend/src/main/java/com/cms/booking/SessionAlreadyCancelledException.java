package com.cms.booking;

import java.util.UUID;

/** 029 FR-005: the Session has zero currently-active Bookings - either already cancelled, or it never had any (research.md R3). */
public class SessionAlreadyCancelledException extends RuntimeException {

    public SessionAlreadyCancelledException(UUID sessionId) {
        super("Session " + sessionId + " has nothing to cancel");
    }
}
