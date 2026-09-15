package com.cms.scheduling;

import java.util.UUID;

/** 019: exhausted retry attempts under pathological contention - only reachable in extreme, sustained concurrent load. */
public class TokenIssuanceFailedException extends RuntimeException {

    public TokenIssuanceFailedException(UUID sessionId) {
        super("Failed to issue a token for Session " + sessionId + " after repeated attempts");
    }
}
