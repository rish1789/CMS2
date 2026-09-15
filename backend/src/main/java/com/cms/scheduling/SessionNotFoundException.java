package com.cms.scheduling;

import java.util.UUID;

public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(UUID sessionId) {
        super("No Session with id " + sessionId);
    }
}
