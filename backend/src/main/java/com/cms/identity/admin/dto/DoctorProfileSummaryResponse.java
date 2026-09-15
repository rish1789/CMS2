package com.cms.identity.admin.dto;

import com.cms.identity.doctor.DoctorProfile;
import java.time.Instant;
import java.util.UUID;

public record DoctorProfileSummaryResponse(
        UUID doctorProfileId,
        UUID accountId,
        String accountName,
        String accountEmail,
        String specialization,
        String licenseNumber,
        int experienceYears,
        boolean licenseVerified,
        boolean visible,
        boolean rejected,
        String rejectionReason,
        String rejectionDetail,
        Instant rejectedAt,
        String rejectedBy) {

    public static DoctorProfileSummaryResponse from(DoctorProfile profile) {
        return new DoctorProfileSummaryResponse(
                profile.getId(),
                profile.getAccount().getId(),
                profile.getAccount().getName(),
                profile.getAccount().getEmail(),
                profile.getSpecialization(),
                profile.getLicenseNumber(),
                profile.getExperienceYears(),
                profile.isLicenseVerified(),
                profile.isVisible(),
                profile.isRejected(),
                profile.getRejectionReason() == null ? null : profile.getRejectionReason().name(),
                profile.getRejectionDetail(),
                profile.getRejectedAt(),
                profile.getRejectedBy());
    }
}
