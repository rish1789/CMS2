package com.cms.notification;

import java.util.UUID;

/** 011 Edge Cases: an event whose window has already lapsed (or which is otherwise not PENDING) can never be actioned. */
public class NotificationEventAlreadyExpiredException extends RuntimeException {

    public NotificationEventAlreadyExpiredException(UUID eventId) {
        super("NotificationEvent " + eventId + " is no longer actionable");
    }
}
