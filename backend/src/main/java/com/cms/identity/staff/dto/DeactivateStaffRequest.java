package com.cms.identity.staff.dto;

/** Employee deactivation modal: {@code reason} must match a RoleAssignment.DeactivationReason name. */
public record DeactivateStaffRequest(String reason) {}
