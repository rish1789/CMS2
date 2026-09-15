package com.cms.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.notification.NotificationEvent;
import com.cms.notification.NotificationEventStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 011 FR-007, spec US2 AC1: a lapsed, unactioned event is EXPIRED after expireDue(). */
class ExpireDueSweepTest extends AbstractNotificationIntegrationTest {

    @Test
    void lapsedUnactionedEventIsExpiredAfterSweep() {
        var account = savePatientAccount(true, true, "9812345670");
        NotificationEvent event = notificationEventService.publish(
                account.getId(), "test.event", null, Instant.now().minusSeconds(60));

        int expiredCount = notificationEventService.expireDue();

        assertThat(expiredCount).isGreaterThanOrEqualTo(1);
        NotificationEvent reread = notificationEventService.get(event.getId());
        assertThat(reread.getStatus()).isEqualTo(NotificationEventStatus.EXPIRED);
    }
}
