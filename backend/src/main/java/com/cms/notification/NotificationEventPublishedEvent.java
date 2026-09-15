package com.cms.notification;

import com.cms.patient.account.PatientAccount;
import java.time.Instant;
import java.util.UUID;

/**
 * Published exactly once per successful {@link NotificationEventService#publish} call
 * (037 FR-005: the listening side, not this record, is responsible for only acting after
 * the publishing transaction commits). Plain POJO event (not extending
 * {@code ApplicationEvent}), mirroring {@code com.cms.identity.admin.ClinicDeVerifiedEvent}
 * - a snapshot of everything {@link NotificationDeliveryListener} needs (research.md), so
 * it never needs its own extra queries.
 */
public record NotificationEventPublishedEvent(
        UUID notificationEventId,
        String eventType,
        String payload,
        boolean pushEligible,
        boolean smsEligible,
        String mobile,
        String email,
        Instant occurredAt) {

    public static NotificationEventPublishedEvent of(NotificationEvent event, PatientAccount patientAccount) {
        return new NotificationEventPublishedEvent(
                event.getId(),
                event.getEventType(),
                event.getPayload(),
                event.isPushEligible(),
                event.isSmsEligible(),
                patientAccount.getMobile(),
                patientAccount.getEmail(),
                Instant.now());
    }
}
