package com.cms.notification.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cms.notification.NotificationEventNotFoundException;
import com.cms.notification.PatientAccountNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 011 FR-001/FR-008: publish/get against unknown ids throw the correct domain exceptions. */
class PublishAccountAndEventNotFoundTest extends AbstractNotificationIntegrationTest {

    @Test
    void publishForUnknownPatientAccountThrowsNotFound() {
        UUID unknownId = UUID.randomUUID();

        assertThatThrownBy(() -> notificationEventService.publish(unknownId, "test.event", null, null))
                .isInstanceOf(PatientAccountNotFoundException.class);
    }

    @Test
    void getForUnknownEventThrowsNotFound() {
        UUID unknownId = UUID.randomUUID();

        assertThatThrownBy(() -> notificationEventService.get(unknownId))
                .isInstanceOf(NotificationEventNotFoundException.class);
    }
}
