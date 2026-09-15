package com.cms.identity.account.dto;

import jakarta.validation.constraints.NotBlank;

/** 006-staff-login-dual-identifier: {@code identifier} may be either an email or a staff code. */
public record StaffLoginRequest(@NotBlank String identifier, @NotBlank String password) {}
