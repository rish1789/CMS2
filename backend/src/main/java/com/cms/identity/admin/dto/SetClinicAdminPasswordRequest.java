package com.cms.identity.admin.dto;

/**
 * Body of {@code POST .../clinics/{id}/set-admin-password}. {@code newPassword} is validated
 * against {@code PasswordPolicyValidator} in the service layer (mirrors RejectRequest's own
 * "validated in the service, not via Bean Validation" precedent), since a failed check needs the
 * same INVALID_PASSWORD/failedRules shape as every other password-setting path in this codebase.
 */
public record SetClinicAdminPasswordRequest(String newPassword) {}
