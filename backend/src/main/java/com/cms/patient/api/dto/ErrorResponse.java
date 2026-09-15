package com.cms.patient.api.dto;

import java.util.List;

/**
 * Generic error response shape (contracts/patient-account.md). {@code failedRules} is
 * populated only for {@code INVALID_PASSWORD}; {@code field} only for
 * {@code MISSING_REQUIRED_FIELD}. Never includes any part of a submitted password.
 */
public record ErrorResponse(String error, String message, List<String> failedRules, String field) {

    public static ErrorResponse of(String error, String message) {
        return new ErrorResponse(error, message, null, null);
    }

    public static ErrorResponse withFailedRules(String error, String message, List<String> failedRules) {
        return new ErrorResponse(error, message, failedRules, null);
    }

    public static ErrorResponse withField(String error, String message, String field) {
        return new ErrorResponse(error, message, null, field);
    }
}
