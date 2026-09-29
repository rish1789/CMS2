package com.cms.protection.exception;

import com.cms.identity.api.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 060-booking-abuse-prevention: maps contracts/booking-protection.md's error shapes for the `protection` module. */
@RestControllerAdvice
public class ProtectionExceptionHandler {

    @ExceptionHandler(ProtectionForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ProtectionForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("FORBIDDEN", e.getMessage()));
    }

    @ExceptionHandler(FlagNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFlagNotFound(FlagNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of("FLAG_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(FlagAlreadyResolvedException.class)
    public ResponseEntity<ErrorResponse> handleFlagAlreadyResolved(FlagAlreadyResolvedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("FLAG_ALREADY_RESOLVED", e.getMessage()));
    }

    @ExceptionHandler(UnrecognizedSettingException.class)
    public ResponseEntity<ErrorResponse> handleUnrecognizedSetting(UnrecognizedSettingException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("UNRECOGNIZED_SETTING", e.getMessage()));
    }

    @ExceptionHandler(InvalidSettingValueException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSettingValue(InvalidSettingValueException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("INVALID_SETTING_VALUE", e.getMessage()));
    }

    @ExceptionHandler(ProtectionSettingNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProtectionSettingNotFound(ProtectionSettingNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of("SETTING_NOT_FOUND", e.getMessage()));
    }
}
