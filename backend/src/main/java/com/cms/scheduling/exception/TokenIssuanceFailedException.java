package com.cms.scheduling.exception;



import java.util.UUID;

/**
 * 019/067: a token could not be issued within the session lock's wait bound - only reachable when
 * a session's issuance is stalled, never from ordinary concurrent bookings (067 research.md
 * Decision 5). Mapped to 503 TOKEN_ISSUANCE_FAILED: retry later.
 */
public class TokenIssuanceFailedException extends RuntimeException {

    public TokenIssuanceFailedException(UUID sessionId) {
        super("Could not issue a token for Session " + sessionId + " in time - please try again");
    }
}
