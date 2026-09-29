package com.cms.patient.api;

import com.cms.patient.account.exception.AccountNotFoundException;
import com.cms.patient.account.exception.EmailAlreadyInUseException;
import com.cms.patient.account.exception.IncorrectPasswordException;
import com.cms.patient.account.exception.InvalidMobileNumberException;
import com.cms.patient.account.exception.InvalidPasswordException;
import com.cms.patient.account.exception.MissingRequiredFieldException;
import com.cms.patient.account.exception.SignupFailedException;
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

    /** Login: email matched no registered PatientAccount. */
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("ACCOUNT_NOT_FOUND", e.getMessage()));
    }

    /** Login: email matched a real PatientAccount, but the password didn't match. */
    @ExceptionHandler(IncorrectPasswordException.class)
    public ResponseEntity<ErrorResponse> handleIncorrectPassword(IncorrectPasswordException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("INCORRECT_PASSWORD", e.getMessage()));
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
