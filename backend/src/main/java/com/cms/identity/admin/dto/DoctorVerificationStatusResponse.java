package com.cms.identity.admin.dto;

import java.util.UUID;

public record DoctorVerificationStatusResponse(UUID doctorProfileId, boolean licenseVerified) {}
