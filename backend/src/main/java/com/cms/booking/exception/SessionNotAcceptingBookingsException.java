package com.cms.booking.exception;

import java.util.UUID;

/**
 * 065-phase1-stabilization (research.md R4): the session is cancelled - wholly, or over a range
 * covering the requested time - or a queue/walk-in request targets a session whose date has passed.
 * A past or elapsed timed slot keeps its own {@link SlotDateInThePastException}.
 */
public class SessionNotAcceptingBookingsException extends RuntimeException {

    public SessionNotAcceptingBookingsException(UUID sessionId) {
        super("Session " + sessionId + " is not accepting bookings");
    }
}
