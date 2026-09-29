package com.cms.booking.exception;



import java.util.UUID;

/**
 * 029 FR-005, redefined by 065-phase1-stabilization (FR-009): the Session is already whole-cancelled
 * (a whole-session cancellation record exists). An empty session is no longer refused.
 */
public class SessionAlreadyCancelledException extends RuntimeException {

    public SessionAlreadyCancelledException(UUID sessionId) {
        super("Session " + sessionId + " is already cancelled");
    }
}
