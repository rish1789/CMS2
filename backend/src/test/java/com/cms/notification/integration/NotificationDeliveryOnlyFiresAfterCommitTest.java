package com.cms.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * 037 FR-005/SC-004: a publish() call whose enclosing transaction never commits (here:
 * Spring's test-managed transaction, rolled back automatically after the test) produces
 * zero sends - publish()'s own @Transactional (REQUIRED propagation) joins this outer
 * transaction rather than committing independently, so AFTER_COMMIT never fires.
 */
class NotificationDeliveryOnlyFiresAfterCommitTest extends AbstractNotificationIntegrationTest {

    @Test
    @Transactional
    void publishInsideARolledBackTransactionNeverSends() {
        var account = savePatientAccount(true, true, "9812345670");

        notificationEventService.publish(account.getId(), "test.event", "hello", null);

        assertThat(recordedSends()).isEmpty();
    }
}
