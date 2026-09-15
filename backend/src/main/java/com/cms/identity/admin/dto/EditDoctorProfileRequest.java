package com.cms.identity.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EditDoctorProfileRequest(
        @NotBlank String specialization,
        @NotBlank String licenseNumber,
        @NotNull Integer experienceYears,
        @NotNull Boolean visible) {}
