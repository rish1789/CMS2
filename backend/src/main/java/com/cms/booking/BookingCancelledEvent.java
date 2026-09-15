package com.cms.booking;

import java.time.Instant;
import java.util.UUID;

/**
 * 028: the sole waitlist-bump trigger in the system - published exactly once per genuine
 * {@code ACTIVE -> CANCELLED} transition (FR-005/SC-003), never on a lost race or an
 * already-cancelled Booking. Plain POJO event (not extending {@code ApplicationEvent}),
 * mirroring {@code com.cms.identity.admin.ClinicDeVerifiedEvent} and
 * {@code com.cms.notification.NotificationEventPublishedEvent}'s identical shape.
 *
 * <p>Not persisted anywhere. The backlog's 028-waitlist-matching-longest-waiting feature (a
 * later feature, unrelated to this spec directory's own sequence number) will add its own
 * listener - this feature deliberately has none, per the constitution's event-driven
 * cross-module communication requirement (Principle III).
 */
public record BookingCancelledEvent(UUID bookingId, UUID slotId, Instant occurredAt) {

    public static BookingCancelledEvent of(UUID bookingId, UUID slotId) {
        return new BookingCancelledEvent(bookingId, slotId, Instant.now());
    }
}
