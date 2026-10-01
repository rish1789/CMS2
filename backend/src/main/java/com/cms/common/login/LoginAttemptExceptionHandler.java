package com.cms.common.login;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 075-login-hardening: one 429 shape for both login realms, with {@code Retry-After} (exposed by CorsConfig, 071). */
@RestControllerAdvice
public class LoginAttemptExceptionHandler {

    public record LoginLockedErrorResponse(String error, String message, long retryAfterSeconds) {}

    @ExceptionHandler(LoginTemporarilyLockedException.class)
    public ResponseEntity<LoginLockedErrorResponse> handleLocked(LoginTemporarilyLockedException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(e.retryAfterSeconds()))
                .body(new LoginLockedErrorResponse("TOO_MANY_LOGIN_ATTEMPTS", e.getMessage(), e.retryAfterSeconds()));
    }
}
