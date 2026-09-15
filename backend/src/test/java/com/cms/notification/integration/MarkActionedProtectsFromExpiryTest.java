package com.cms.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.notification.NotificationEvent;
import com.cms.notification.NotificationEventStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 011 FR-006, spec US2 AC2-AC3: an event actioned before its window lapses is never expired, and its window is queryable before expiry. */
class MarkActionedProtectsFromExpiryTest extends AbstractNotificationIntegrationTest {

    @Test
    void actionedEventStaysActionedAfterASubsequentSweep() {
        // A lapsed window is used deliberately: markActioned only guards on status (not on
        // wall-clock time), so this is the deterministic way to prove the sweep respects
        // ACTIONED regardless of whether the window has technically lapsed by the time it
        // runs - exactly the FR-006 guarantee ("permanently excludes it from ever being
        // auto-expired"), without relying on a real-time sleep between publish and sweep.
        var account = savePatientAccount(true, true, "9812345670");
        Instant expiry = Instant.now().minusSeconds(60);
        NotificationEvent event = notificationEventService.publish(account.getId(), "test.event", null, expiry);

        assertThat(notificationEventService.get(event.getId()).getExpiresAt()).isEqualTo(expiry);

        NotificationEvent actioned = notificationEventService.markActioned(event.getId());
        assertThat(actioned.getStatus()).isEqualTo(NotificationEventStatus.ACTIONED);

        notificationEventService.expireDue();

        assertThat(notificationEventService.get(event.getId()).getStatus())
                .isEqualTo(NotificationEventStatus.ACTIONED);
    }
}
