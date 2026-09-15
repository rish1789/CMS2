package com.cms.identity.staff.dto;

import com.cms.identity.account.RoleAssignment;
import com.cms.identity.doctor.DoctorProfile;
import java.time.Instant;
import java.util.UUID;

/**
 * specialization/experienceYears are Doctor-only (null for ClinicAdmin/Operations, which have
 * no DoctorProfile) - the caller passes whatever bulk-loaded profile matches this row's account,
 * or null if there isn't one (see ClinicStaffController).
 */
public record StaffSummaryResponse(
        UUID roleAssignmentId,
        UUID accountId,
        String name,
        String staffCode,
        String role,
        String email,
        String mobile,
        String specialization,
        Integer experienceYears,
        Instant joinedAt,
        boolean active) {

    public static StaffSummaryResponse from(RoleAssignment roleAssignment, DoctorProfile doctorProfile) {
        return new StaffSummaryResponse(
                roleAssignment.getId(),
                roleAssignment.getAccount().getId(),
                roleAssignment.getAccount().getName(),
                roleAssignment.getAccount().getStaffCode(),
                roleAssignment.getRole().name(),
                roleAssignment.getAccount().getEmail(),
                roleAssignment.getAccount().getMobile(),
                doctorProfile == null ? null : doctorProfile.getSpecialization(),
                doctorProfile == null ? null : doctorProfile.getExperienceYears(),
                roleAssignment.getCreatedAt(),
                roleAssignment.isActive());
    }
}
