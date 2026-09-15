package com.cms.patient.api.dto;

import java.util.UUID;

public record LoginResponse(String token, UUID patientAccountId, String email) {}
