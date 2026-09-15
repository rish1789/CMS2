package com.cms.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 037: v1's only {@link NotificationSender} - no real provider is connected (FR-002).
 * Requires no configuration, credential, or external client (FR-004) - there is nothing
 * here that can fail at startup.
 */
@Component
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void send(String channel, String recipient, String message) {
        log.info("[NOTIFICATION STUB] channel={} recipient={} message={}", channel, recipient, message);
    }
}
