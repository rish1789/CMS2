package com.cms.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cms.notification.NotificationEvent;
import com.cms.notification.NotificationEventAlreadyExpiredException;
import com.cms.notification.NotificationEventStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 011 Edge Cases: markActioned on an already-EXPIRED event is rejected, not silently allowed. */
class MarkActionedOnExpiredEventRejectedTest extends AbstractNotificationIntegrationTest {

    @Test
    void markActionedOnAlreadyExpiredEventThrowsAndLeavesStatusExpired() {
        var account = savePatientAccount(true, true, "9812345670");
        NotificationEvent event = notificationEventService.publish(
                account.getId(), "test.event", null, Instant.now().minusSeconds(60));
        notificationEventService.expireDue();

        assertThatThrownBy(() -> notificationEventService.markActioned(event.getId()))
                .isInstanceOf(NotificationEventAlreadyExpiredException.class);

        assertThat(notificationEventService.get(event.getId()).getStatus())
                .isEqualTo(NotificationEventStatus.EXPIRED);
    }
}
