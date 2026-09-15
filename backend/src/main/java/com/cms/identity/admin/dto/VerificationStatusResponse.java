package com.cms.identity.admin.dto;

import java.util.UUID;

public record VerificationStatusResponse(UUID clinicId, boolean verified) {}
