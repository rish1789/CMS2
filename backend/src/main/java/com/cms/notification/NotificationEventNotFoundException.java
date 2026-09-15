package com.cms.notification;

import java.util.UUID;

public class NotificationEventNotFoundException extends RuntimeException {

    public NotificationEventNotFoundException(UUID eventId) {
        super("No NotificationEvent with id " + eventId);
    }
}
