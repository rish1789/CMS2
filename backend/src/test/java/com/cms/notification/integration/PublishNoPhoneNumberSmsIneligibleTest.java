package com.cms.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.notification.NotificationEvent;
import org.junit.jupiter.api.Test;

/** 011 FR-004: no mobile on file makes SMS ineligible regardless of the smsOptIn flag. */
class PublishNoPhoneNumberSmsIneligibleTest extends AbstractNotificationIntegrationTest {

    @Test
    void smsOptedInButNoMobileOnFileMakesSmsIneligible() {
        var account = savePatientAccount(true, true, null);

        NotificationEvent event = notificationEventService.publish(account.getId(), "test.event", null, null);

        assertThat(event.isSmsEligible()).isFalse();
        assertThat(event.isPushEligible()).isTrue();
    }
}
