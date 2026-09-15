package com.cms.notification;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationEventRepository extends JpaRepository<NotificationEvent, UUID> {

    /**
     * 011 FR-007: a single bulk conditional update, not a read-then-write loop - naturally
     * idempotent (a second call matches zero rows) and race-safe against a concurrent
     * {@code markActioned} (whichever transaction commits first wins the row; see
     * research.md). Backed by {@code idx_notification_event_status_expires_at} (V6).
     */
    @Modifying
    @Query(
            "UPDATE NotificationEvent e SET e.status = com.cms.notification.NotificationEventStatus.EXPIRED "
                    + "WHERE e.status = com.cms.notification.NotificationEventStatus.PENDING "
                    + "AND e.expiresAt IS NOT NULL AND e.expiresAt < :now")
    int expireDuePending(@Param("now") Instant now);

    /**
     * 011 Convergence (Constitution IV): a data-layer-guarded conditional update, not a
     * read-then-check-then-write - closes the lost-update race against a concurrent
     * {@link #expireDuePending}: whichever of the two commits first for a given row wins,
     * the loser's WHERE clause matches zero rows.
     */
    @Modifying
    @Query(
            "UPDATE NotificationEvent e SET e.status = com.cms.notification.NotificationEventStatus.ACTIONED "
                    + "WHERE e.id = :id AND e.status = com.cms.notification.NotificationEventStatus.PENDING")
    int markActionedIfPending(@Param("id") UUID id);
}
