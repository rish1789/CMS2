package com.cms.identity.admin.dto;

import java.util.UUID;

/**
 * super-admin-console-redesign real-bug-fix 2026-09-16: mirrors OnboardStaffResponse's own
 * "temporary password shown once, cannot be retrieved again" contract - the same precedent
 * already established for a brand-new account applies equally here to a reset one.
 */
public record ResetClinicAdminPasswordResponse(UUID accountId, String email, String temporaryPassword) {}
