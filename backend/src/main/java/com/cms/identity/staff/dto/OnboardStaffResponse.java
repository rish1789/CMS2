package com.cms.identity.staff.dto;

import java.util.UUID;

public record OnboardStaffResponse(
        UUID accountId,
        String email,
        String staffCode,
        String temporaryPassword,
        String role,
        UUID doctorProfileId,
        boolean existingAccount) {}
