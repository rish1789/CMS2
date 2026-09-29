package com.cms.identity.admin.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 062-rejected-clinic-gating (research.md Decision 3): published exactly once per genuine
 * not-rejected -> rejected transition of {@code Clinic.rejected}, never on a repeated reject of an
 * already-rejected clinic. Mirrors {@link ClinicDeVerifiedEvent}: the booking module cancels the
 * clinic's upcoming bookings and the waitlist module closes its open entries, each from its own
 * AFTER_COMMIT listener (Constitution III).
 */
public record ClinicRejectedEvent(UUID clinicId, Instant occurredAt) {

    public static ClinicRejectedEvent of(UUID clinicId) {
        return new ClinicRejectedEvent(clinicId, Instant.now());
    }
}
