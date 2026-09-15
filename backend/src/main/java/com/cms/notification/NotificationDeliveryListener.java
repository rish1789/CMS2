package com.cms.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 037 FR-001/FR-005: fires once per eligible channel, only after the publishing
 * transaction has actually committed (never for a rolled-back {@link NotificationEventService#publish}
 * call) - AFTER_COMMIT is what makes that guarantee structural, not a plain
 * {@code @EventListener} (research.md).
 */
@Component
public class NotificationDeliveryListener {

    private final NotificationSender notificationSender;

    public NotificationDeliveryListener(NotificationSender notificationSender) {
        this.notificationSender = notificationSender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationEventPublished(NotificationEventPublishedEvent event) {
        String message = event.eventType() + (event.payload() != null ? ": " + event.payload() : "");

        if (event.pushEligible()) {
            notificationSender.send("push", event.email(), message);
        }
        if (event.smsEligible()) {
            notificationSender.send("sms", event.mobile(), message);
        }
    }
}
