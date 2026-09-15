package com.cms.identity.admin.dto;

import com.cms.identity.clinic.Clinic;
import java.time.Instant;
import java.util.UUID;

/** One row of contracts/clinic-verification.md's {@code GET .../clinics} response. */
public record ClinicSummaryResponse(
        UUID clinicId,
        String name,
        String address,
        String contactEmail,
        String contactMobile,
        Instant createdAt,
        boolean rejected,
        String rejectionReason,
        String rejectionDetail,
        Instant rejectedAt,
        String rejectedBy) {

    public static ClinicSummaryResponse from(Clinic clinic) {
        return new ClinicSummaryResponse(
                clinic.getId(),
                clinic.getName(),
                clinic.getAddress(),
                clinic.getContactEmail(),
                clinic.getContactMobile(),
                clinic.getCreatedAt(),
                clinic.isRejected(),
                clinic.getRejectionReason() == null ? null : clinic.getRejectionReason().name(),
                clinic.getRejectionDetail(),
                clinic.getRejectedAt(),
                clinic.getRejectedBy());
    }
}
