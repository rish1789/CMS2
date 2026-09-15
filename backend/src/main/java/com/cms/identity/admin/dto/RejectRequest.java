package com.cms.identity.admin.dto;

/**
 * Body of a single-entity reject action, for both {@code POST .../clinics/{id}/reject} and
 * {@code POST .../doctors/{id}/reject}. {@code reasonCode} is validated against the owning
 * entity's own {@code RejectionReason} enum in the service layer (mirrors
 * StaffDeactivationService.parseReason), not via Bean Validation - a blank/invalid value needs
 * its own MISSING_REASON/INVALID_REASON error codes, not the generic validation-failure one.
 */
public record RejectRequest(String reasonCode, String detail) {}
