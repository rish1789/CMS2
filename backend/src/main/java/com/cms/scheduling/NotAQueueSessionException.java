package com.cms.scheduling;

import java.util.UUID;

/** 019 FR-004: this Session's mode is not QUEUE - token issuance only ever applies to Queue/Token Sessions. */
public class NotAQueueSessionException extends RuntimeException {

    public NotAQueueSessionException(UUID sessionId) {
        super("Session " + sessionId + " is not a Queue/Token session");
    }
}
