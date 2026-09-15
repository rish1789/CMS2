package com.cms.notification;

import com.cms.patient.account.PatientAccount;
import com.cms.patient.account.PatientAccountRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 011: the feature's sole public contract - see contracts/notification-event-service.md. */
@Service
public class NotificationEventService {

    private final NotificationEventRepository notificationEventRepository;
    private final PatientAccountRepository patientAccountRepository;
    private final ApplicationEventPublisher eventPublisher;

    public NotificationEventService(
            NotificationEventRepository notificationEventRepository,
            PatientAccountRepository patientAccountRepository,
            ApplicationEventPublisher eventPublisher) {
        this.notificationEventRepository = notificationEventRepository;
        this.patientAccountRepository = patientAccountRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * FR-001/FR-002: computes and snapshots channel eligibility from the patient's
     * current opt-in state, persists the event, and returns - still without itself
     * invoking or waiting on any delivery step. 037 hangs its (log-only, AFTER_COMMIT)
     * send step off the {@link NotificationEventPublishedEvent} published here, the same
     * publish-now/listen-later shape as {@code ClinicDeVerifiedEvent} (003/008) - this
     * method's own transaction never depends on, or is slowed by, that listener.
     */
    @Transactional
    public NotificationEvent publish(UUID patientAccountId, String eventType, String payload, Instant expiresAt) {
        PatientAccount patientAccount = patientAccountRepository
                .findById(patientAccountId)
                .orElseThrow(() -> new PatientAccountNotFoundException(patientAccountId));

        boolean pushEligible = patientAccount.isPushOptIn();
        boolean smsEligible = patientAccount.isSmsOptIn() && patientAccount.getMobile() != null;

        NotificationEvent event =
                new NotificationEvent(patientAccount, eventType, payload, pushEligible, smsEligible, expiresAt);
        NotificationEvent saved = notificationEventRepository.save(event);
        eventPublisher.publishEvent(NotificationEventPublishedEvent.of(saved, patientAccount));
        return saved;
    }

    /**
     * FR-006, Edge Cases: rejects an event that is no longer PENDING (already EXPIRED, or
     * already ACTIONED). The status flip is a data-layer-guarded conditional update
     * (Constitution IV), not a read-then-check-then-write, so it can never lose a race
     * against a concurrent {@link #expireDue()} sweep on the same row (Convergence fix).
     */
    @Transactional
    public NotificationEvent markActioned(UUID eventId) {
        int updated = notificationEventRepository.markActionedIfPending(eventId);
        if (updated == 0) {
            notificationEventRepository
                    .findById(eventId)
                    .orElseThrow(() -> new NotificationEventNotFoundException(eventId));
            throw new NotificationEventAlreadyExpiredException(eventId);
        }
        return notificationEventRepository
                .findById(eventId)
                .orElseThrow(() -> new NotificationEventNotFoundException(eventId));
    }

    /** FR-007: bulk-transitions every lapsed, still-PENDING event to EXPIRED. Idempotent. */
    @Transactional
    public int expireDue() {
        return notificationEventRepository.expireDuePending(Instant.now());
    }

    /** FR-008. */
    @Transactional(readOnly = true)
    public NotificationEvent get(UUID eventId) {
        return notificationEventRepository
                .findById(eventId)
                .orElseThrow(() -> new NotificationEventNotFoundException(eventId));
    }
}
