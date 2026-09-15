package com.cms.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.notification.NotificationEvent;
import com.cms.notification.NotificationEventStatus;
import org.junit.jupiter.api.Test;

/** 011 FR-005, spec US2 AC4: an event published with no expiration window is never touched by expireDue(). */
class ExpireDueLeavesNonTimeBoxedEventsAloneTest extends AbstractNotificationIntegrationTest {

    @Test
    void nonTimeBoxedEventStaysPendingAcrossAnyNumberOfSweeps() {
        var account = savePatientAccount(true, true, "9812345670");
        NotificationEvent event = notificationEventService.publish(account.getId(), "test.event", null, null);

        notificationEventService.expireDue();
        notificationEventService.expireDue();

        assertThat(notificationEventService.get(event.getId()).getStatus())
                .isEqualTo(NotificationEventStatus.PENDING);
    }
}
