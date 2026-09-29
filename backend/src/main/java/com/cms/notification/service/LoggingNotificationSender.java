package com.cms.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 037: v1's only {@link NotificationSender} - no real provider is connected (FR-002).
 * Requires no configuration, credential, or external client (FR-004) - there is nothing
 * here that can fail at startup.
 *
 * <p>047-backend-hardening FR-002/US2: {@code app.notification.log-pii} defaults to {@code
 * false} (fail-safe, matching this project's own JWT-secret/Super-Admin-credential precedent
 * of "unset means the safe behavior, not the convenient one") - a production deployment never
 * logs recipient contact info or message content unless a developer deliberately opts in for
 * local debugging.
 */
@Component
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    private final boolean logPii;

    public LoggingNotificationSender(@Value("${app.notification.log-pii:false}") boolean logPii) {
        this.logPii = logPii;
    }

    @Override
    public void send(String channel, String recipient, String message) {
        if (logPii) {
            log.info("[NOTIFICATION STUB] channel={} recipient={} message={}", channel, recipient, message);
        } else {
            log.info("[NOTIFICATION STUB] channel={} recipient=<redacted> message=<redacted>", channel);
        }
    }
}
