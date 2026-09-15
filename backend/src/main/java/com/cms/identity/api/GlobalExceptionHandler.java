package com.cms.identity.api;

import com.cms.identity.api.dto.ErrorResponse;
import com.cms.identity.clinic.EmailAlreadyInUseException;
import com.cms.identity.clinic.InvalidMobileNumberException;
import com.cms.identity.clinic.InvalidPasswordException;
import com.cms.identity.clinic.MissingRequiredFieldException;
import com.cms.identity.clinic.RegistrationFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain exceptions to the error response shapes in
 * contracts/register-clinic.md. Every branch returns {@link ErrorResponse}, which never
 * carries a password or hash (Constitution Principle IV / T037 security review).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPassword(InvalidPasswordException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.withFailedRules(
                        "INVALID_PASSWORD", "Password does not satisfy the required policy", e.getFailedRules()));
    }

    @ExceptionHandler(InvalidMobileNumberException.class)
    public ResponseEntity<ErrorResponse> handleInvalidMobileNumber(InvalidMobileNumberException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.withField(
                        "INVALID_MOBILE_NUMBER",
                        "Mobile number does not match the Indian numbering plan",
                        e.getField()));
    }

    @ExceptionHandler(EmailAlreadyInUseException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyInUse(EmailAlreadyInUseException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("EMAIL_ALREADY_IN_USE", e.getMessage()));
    }

    @ExceptionHandler(MissingRequiredFieldException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequiredField(MissingRequiredFieldException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.withField("MISSING_REQUIRED_FIELD", e.getMessage(), e.getField()));
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBeanValidation(
            org.springframework.web.bind.MethodArgumentNotValidException e) {
        String field = e.getBindingResult().getFieldErrors().isEmpty()
                ? "unknown"
                : e.getBindingResult().getFieldErrors().get(0).getField();
        return ResponseEntity.badRequest()
                .body(ErrorResponse.withField("MISSING_REQUIRED_FIELD", "A required field is missing or invalid", field));
    }

    @ExceptionHandler(RegistrationFailedException.class)
    public ResponseEntity<ErrorResponse> handleRegistrationFailed(RegistrationFailedException e) {
        return ResponseEntity.internalServerError()
                .body(ErrorResponse.of("REGISTRATION_FAILED", "Registration could not be completed"));
    }
}
