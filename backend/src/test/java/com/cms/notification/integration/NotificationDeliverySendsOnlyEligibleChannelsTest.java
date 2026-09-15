package com.cms.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 037 FR-001/FR-003, spec US1 AC1-AC3: exactly one send per eligible channel, none for
 * ineligible ones. No wait/poll needed - {@code AFTER_COMMIT} listeners run synchronously
 * as part of the publishing transaction's commit, before {@code publish()} returns.
 */
class NotificationDeliverySendsOnlyEligibleChannelsTest extends AbstractNotificationIntegrationTest {

    @Test
    void bothChannelsEligibleProducesOneSendEach() {
        var account = savePatientAccount(true, true, "9812345670");

        notificationEventService.publish(account.getId(), "test.event", "hello", null);

        assertThat(recordedSends()).hasSize(2);
        assertThat(recordedSends())
                .anySatisfy(send -> {
                    assertThat(send.channel()).isEqualTo("push");
                    assertThat(send.recipient()).isEqualTo(account.getEmail());
                })
                .anySatisfy(send -> {
                    assertThat(send.channel()).isEqualTo("sms");
                    assertThat(send.recipient()).isEqualTo("9812345670");
                });
    }

    @Test
    void pushOnlyEligibleProducesOnlyOnePushSend() {
        var account = savePatientAccount(true, false, "9812345670");

        notificationEventService.publish(account.getId(), "test.event", "hello", null);

        assertThat(recordedSends()).hasSize(1);
        assertThat(recordedSends().get(0).channel()).isEqualTo("push");
    }

    @Test
    void neitherChannelEligibleProducesNoSends() {
        var account = savePatientAccount(false, false, "9812345670");

        notificationEventService.publish(account.getId(), "test.event", "hello", null);

        assertThat(recordedSends()).isEmpty();
    }
}
