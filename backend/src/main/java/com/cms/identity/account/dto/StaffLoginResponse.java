package com.cms.identity.account.dto;

import java.util.UUID;

/**
 * 040-super-admin-rbac-login: {@code accountId} is {@code null} and {@code email} holds
 * the configured Super Admin username (never a real email, never parsed as one by any
 * consumer) when {@code role} is {@code "SUPER_ADMIN"} - no database Account row exists
 * for that identity. {@code role} is {@code "STAFF"} or {@code "SUPER_ADMIN"}.
 */
public record StaffLoginResponse(String token, UUID accountId, String email, String role) {}
