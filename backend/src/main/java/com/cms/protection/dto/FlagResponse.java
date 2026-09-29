package com.cms.protection.dto;

import com.cms.protection.domain.SuspiciousActivityFlag;
import java.time.Instant;
import java.util.UUID;

/** 060-booking-abuse-prevention (contracts/booking-protection.md #2): one flag, list/detail shape. */
public record FlagResponse(
        UUID id,
        UUID patientAccountId,
        String patientDisplayName,
        String signalType,
        String reason,
        Instant detectedAt,
        String status,
        Instant resolvedAt,
        String resolvedBy) {

    public static FlagResponse of(SuspiciousActivityFlag flag) {
        // The patient's display name lives on the clinic-scoped Patient record, not the global
        // PatientAccount; email is always present on every account, so it's used here rather
        // than an extra per-clinic Patient lookup that might not even resolve (e.g. a flag whose
        // only evidence is rate-limited attempts with no actual booking ever created).
        return new FlagResponse(
                flag.getId(),
                flag.getPatientAccount().getId(),
                flag.getPatientAccount().getEmail(),
                flag.getSignalType().name(),
                flag.getReason(),
                flag.getDetectedAt(),
                flag.getStatus().name(),
                flag.getResolvedAt(),
                flag.getResolvedBy());
    }
}
