package com.cms.protection.dto;

import jakarta.validation.constraints.NotBlank;

/** 060-booking-abuse-prevention (contracts/booking-protection.md #4): `PUT /api/v1/admin/protection-settings/{name}`. */
public record UpdateSettingRequest(@NotBlank String value) {}
