package com.cms.identity.staff.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * {@code role} is intentionally a plain String, not the {@code RoleAssignment.Role} enum
 * directly - so a client sending {@code "ClinicAdmin"}/{@code "SuperAdmin"} (or garbage)
 * deserializes successfully and is rejected by {@code StaffOnboardingService} as
 * {@code InvalidRoleException} (400 INVALID_ROLE), rather than failing Jackson
 * deserialization with a generic 400 that wouldn't distinguish "bad role" from "malformed
 * JSON" (FR-003).
 */
public record OnboardStaffRequest(
        @NotBlank String name, @NotBlank @Email String email, String mobile, @NotBlank String role, @Valid DoctorDto doctor) {

    public record DoctorDto(String specialization, String licenseNumber, Integer experienceYears) {}
}
