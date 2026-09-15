package com.cms.patient.api.dto;

import java.util.UUID;

public record SignupResponse(UUID patientAccountId, String email) {}
