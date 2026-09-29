package com.cms.scheduling.exception;



import java.util.UUID;

public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(UUID sessionId) {
        super("No Session with id " + sessionId);
    }
}
