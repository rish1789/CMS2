package com.cms.notification.exception;



import java.util.UUID;

public class NotificationEventNotFoundException extends RuntimeException {

    public NotificationEventNotFoundException(UUID eventId) {
        super("No NotificationEvent with id " + eventId);
    }
}
