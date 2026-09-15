package com.cms.identity.admin;

import java.time.Instant;
import java.util.UUID;

/**
 * Published exactly once per genuine {@code true -> false} transition of
 * {@code Clinic.verified} (FR-008, data-model.md) - never on a repeated un-verify call
 * against an already-unverified clinic (FR-007). Plain POJO event (not extending
 * {@code ApplicationEvent}), per modern Spring's arbitrary-object event support.
 *
 * <p>Not persisted anywhere. 008-deverification-cascade-auto-cancel-bookings will add
 * its own {@code @EventListener} for this later - this feature deliberately has none,
 * per the constitution's event-driven cross-module communication requirement
 * (Principle III) and research.md's decision.
 */
public record ClinicDeVerifiedEvent(UUID clinicId, Instant occurredAt) {

    public static ClinicDeVerifiedEvent of(UUID clinicId) {
        return new ClinicDeVerifiedEvent(clinicId, Instant.now());
    }
}
