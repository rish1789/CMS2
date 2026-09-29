package com.cms.identity.staff.dto;

import java.util.UUID;

/**
 * real-bug-fix 2026-09-17: mirrors com.cms.identity.admin's ResetClinicAdminPasswordResponse -
 * the temporary/chosen password is returned exactly once here, never stored or logged in
 * plaintext, same "shown once, hand it to the staff member directly" contract.
 */
public record ResetStaffPasswordResponse(UUID accountId, String email, String staffCode, String temporaryPassword) {}
