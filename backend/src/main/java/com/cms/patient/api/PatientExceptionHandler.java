package com.cms.patient.api;

import com.cms.patient.account.EmailAlreadyInUseException;
import com.cms.patient.account.InvalidCredentialsException;
import com.cms.patient.account.InvalidMobileNumberException;
import com.cms.patient.account.InvalidPasswordException;
import com.cms.patient.account.MissingRequiredFieldException;
import com.cms.patient.account.SignupFailedException;
import com.cms.patient.api.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain exceptions to the error response shapes in contracts/patient-account.md.
 * Every branch returns {@link ErrorResponse}, which never carries a password, hash, or
 * token (Constitution Principle IV / T031 security review).
 */
@RestControllerAdvice(basePackages = "com.cms.patient.api")
public class PatientExceptionHandler {

    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPassword(InvalidPasswordException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.withFailedRules(
                        "INVALID_PASSWORD", "Password does not satisfy the required policy", e.getFailedRules()));
    }

    @ExceptionHandler(InvalidMobileNumberException.class)
    public ResponseEntity<ErrorResponse> handleInvalidMobileNumber(InvalidMobileNumberException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(
                        "INVALID_MOBILE_NUMBER", "Mobile number does not match the Indian numbering plan"));
    }

    @ExceptionHandler(EmailAlreadyInUseException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyInUse(EmailAlreadyInUseException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("EMAIL_ALREADY_IN_USE", e.getMessage()));
    }

    @ExceptionHandler(MissingRequiredFieldException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequiredField(MissingRequiredFieldException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.withField("MISSING_REQUIRED_FIELD", e.getMessage(), e.getField()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("INVALID_CREDENTIALS", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException e) {
        String field = e.getBindingResult().getFieldErrors().isEmpty()
                ? "unknown"
                : e.getBindingResult().getFieldErrors().get(0).getField();
        return ResponseEntity.badRequest()
                .body(ErrorResponse.withField("MISSING_REQUIRED_FIELD", "A required field is missing or invalid", field));
    }

    @ExceptionHandler(SignupFailedException.class)
    public ResponseEntity<ErrorResponse> handleSignupFailed(SignupFailedException e) {
        return ResponseEntity.internalServerError().body(ErrorResponse.of("SIGNUP_FAILED", "Signup could not be completed"));
    }
}
