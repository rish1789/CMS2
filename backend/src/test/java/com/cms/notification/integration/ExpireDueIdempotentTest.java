package com.cms.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.notification.NotificationEvent;
import com.cms.notification.NotificationEventStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 011 FR-007, spec US2 AC1: expireDue() is safely re-runnable with no further side effect. */
class ExpireDueIdempotentTest extends AbstractNotificationIntegrationTest {

    @Test
    void secondSweepMakesNoFurtherChangeAndReturnsZero() {
        var account = savePatientAccount(true, true, "9812345670");
        NotificationEvent event = notificationEventService.publish(
                account.getId(), "test.event", null, Instant.now().minusSeconds(60));

        notificationEventService.expireDue();
        int secondSweepCount = notificationEventService.expireDue();

        assertThat(secondSweepCount).isZero();
        assertThat(notificationEventService.get(event.getId()).getStatus())
                .isEqualTo(NotificationEventStatus.EXPIRED);
    }
}
