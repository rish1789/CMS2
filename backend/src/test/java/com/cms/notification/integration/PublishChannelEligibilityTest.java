package com.cms.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.notification.NotificationEvent;
import org.junit.jupiter.api.Test;

/** 011 FR-002/FR-003, spec US1 AC1-AC3: channel eligibility is computed and snapshotted at publish time. */
class PublishChannelEligibilityTest extends AbstractNotificationIntegrationTest {

    @Test
    void bothChannelsOptedInWithMobilePresentAreBothEligible() {
        var account = savePatientAccount(true, true, "9812345670");

        NotificationEvent event = notificationEventService.publish(account.getId(), "test.event", null, null);

        assertThat(event.isPushEligible()).isTrue();
        assertThat(event.isSmsEligible()).isTrue();
    }

    @Test
    void smsOptedOutMakesSmsIneligibleButPushUnaffected() {
        var account = savePatientAccount(true, false, "9812345670");

        NotificationEvent event = notificationEventService.publish(account.getId(), "test.event", null, null);

        assertThat(event.isPushEligible()).isTrue();
        assertThat(event.isSmsEligible()).isFalse();
    }

    @Test
    void bothChannelsOptedOutStillCreatesTheEventWithZeroEligibleChannels() {
        var account = savePatientAccount(false, false, "9812345670");

        NotificationEvent event = notificationEventService.publish(account.getId(), "test.event", null, null);

        assertThat(event.getId()).isNotNull();
        assertThat(event.isPushEligible()).isFalse();
        assertThat(event.isSmsEligible()).isFalse();
    }

    @Test
    void laterPreferenceChangeDoesNotRetroactivelyAffectAnAlreadyPublishedEvent() {
        var account = savePatientAccount(true, true, "9812345670");

        NotificationEvent firstEvent = notificationEventService.publish(account.getId(), "test.event", null, null);
        assertThat(firstEvent.isSmsEligible()).isTrue();

        account.setSmsOptIn(false);
        patientAccountRepository.save(account);

        NotificationEvent reread = notificationEventService.get(firstEvent.getId());
        assertThat(reread.isSmsEligible()).isTrue();

        NotificationEvent secondEvent = notificationEventService.publish(account.getId(), "test.event", null, null);
        assertThat(secondEvent.isSmsEligible()).isFalse();
    }
}
