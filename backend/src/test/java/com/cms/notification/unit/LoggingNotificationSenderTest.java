package com.cms.notification.unit;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cms.notification.service.LoggingNotificationSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * 047-backend-hardening FR-002/SC-002: proves the notification stub never writes recipient
 * contact info or message content to logs unless explicitly opted in. Pure JUnit + Logback's
 * own in-memory ListAppender (already on the classpath transitively via
 * spring-boot-starter-logging) - no Spring context, no Docker, no new dependency.
 */
class LoggingNotificationSenderTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(LoggingNotificationSender.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void defaultOffLogsNoRecipientOrMessageContent() {
        new LoggingNotificationSender(false).send("EMAIL", "patient@example.com", "Your appointment is confirmed");

        String logged = appender.list.get(0).getFormattedMessage();
        assertThat(logged).doesNotContain("patient@example.com");
        assertThat(logged).doesNotContain("Your appointment is confirmed");
        assertThat(logged).contains("EMAIL");
    }

    @Test
    void explicitlyEnabledLogsTheFullLine() {
        new LoggingNotificationSender(true).send("EMAIL", "patient@example.com", "Your appointment is confirmed");

        String logged = appender.list.get(0).getFormattedMessage();
        assertThat(logged).contains("patient@example.com");
        assertThat(logged).contains("Your appointment is confirmed");
    }
}
