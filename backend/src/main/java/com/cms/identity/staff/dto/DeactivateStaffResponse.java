package com.cms.identity.staff.dto;

import java.util.UUID;

/** Per contracts/staff-deactivation.md's success response shape. */
public record DeactivateStaffResponse(UUID accountId, UUID clinicId, String role, boolean active) {}
