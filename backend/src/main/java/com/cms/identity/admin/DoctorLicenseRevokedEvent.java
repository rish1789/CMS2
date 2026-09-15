package com.cms.identity.admin;

import java.time.Instant;
import java.util.UUID;

/**
 * Published exactly once per genuine {@code true -> false} transition of
 * {@code DoctorProfile.licenseVerified} performed by {@link DoctorVerificationService#revoke},
 * never by 006's automatic edit-triggered reset. Plain POJO event (not extending
 * {@code ApplicationEvent}), mirroring {@link ClinicDeVerifiedEvent} exactly.
 *
 * <p>Not persisted anywhere. 033-deverification-cascade-auto-cancel's own
 * {@code DeVerificationCascadeListener} (in {@code com.cms.booking}) is this event's sole
 * consumer.
 */
public record DoctorLicenseRevokedEvent(UUID doctorProfileId, Instant occurredAt) {

    public static DoctorLicenseRevokedEvent of(UUID doctorProfileId) {
        return new DoctorLicenseRevokedEvent(doctorProfileId, Instant.now());
    }
}
