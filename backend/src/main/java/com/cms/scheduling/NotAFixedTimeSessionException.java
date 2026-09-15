package com.cms.scheduling;

import java.util.UUID;

/** 025 spec Assumptions/Edge Cases: this Session's mode is not FIXED_TIME - walk-in priority insertion (buffer slots, no-show-freed slots) has no Queue-mode equivalent; Queue-mode walk-ins are served by 018's own on-demand token issuance instead. */
public class NotAFixedTimeSessionException extends RuntimeException {

    public NotAFixedTimeSessionException(UUID sessionId) {
        super("Session " + sessionId + " is not a Fixed-Time session");
    }
}
