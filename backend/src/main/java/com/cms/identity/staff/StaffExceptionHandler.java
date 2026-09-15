package com.cms.identity.staff;

import com.cms.identity.account.InvalidCredentialsException;
import com.cms.identity.api.dto.ErrorResponse;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the exception types new to this feature to contracts/staff-onboarding.md's error
 * shapes. {@code EmailAlreadyInUseException}, {@code InvalidMobileNumberException},
 * {@code MissingRequiredFieldException}, and Bean Validation failures are already handled
 * application-wide by 001's {@code GlobalExceptionHandler} (reused directly, not
 * duplicated here) since this feature throws the same exception classes.
 */
@RestControllerAdvice
public class StaffExceptionHandler {

    @ExceptionHandler(InvalidRoleException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRole(InvalidRoleException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("INVALID_ROLE", e.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("FORBIDDEN", e.getMessage()));
    }

    /** 041-staff-console-pickers: distinct from {@link ForbiddenException} - see that exception's own message. */
    @ExceptionHandler(NotStaffedAtClinicException.class)
    public ResponseEntity<ErrorResponse> handleNotStaffedAtClinic(NotStaffedAtClinicException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("FORBIDDEN", e.getMessage()));
    }

    @ExceptionHandler(OnboardingFailedException.class)
    public ResponseEntity<ErrorResponse> handleOnboardingFailed(OnboardingFailedException e) {
        return ResponseEntity.internalServerError()
                .body(ErrorResponse.of("ONBOARDING_FAILED", "Onboarding could not be completed"));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleClinicNotFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of("CLINIC_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("INVALID_CREDENTIALS", e.getMessage()));
    }

    @ExceptionHandler(RoleAssignmentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRoleAssignmentNotFound(RoleAssignmentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(LastActiveClinicAdminException.class)
    public ResponseEntity<ErrorResponse> handleLastActiveClinicAdmin(LastActiveClinicAdminException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("LAST_ACTIVE_CLINIC_ADMIN", e.getMessage()));
    }

    @ExceptionHandler(SpecializationMismatchException.class)
    public ResponseEntity<ErrorResponse> handleSpecializationMismatch(SpecializationMismatchException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("SPECIALIZATION_MISMATCH", "License number matches an existing doctor with a different specialization on file"));
    }

    @ExceptionHandler(MissingDeactivationReasonException.class)
    public ResponseEntity<ErrorResponse> handleMissingDeactivationReason(MissingDeactivationReasonException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("MISSING_REASON", e.getMessage()));
    }

    @ExceptionHandler(InvalidDeactivationReasonException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDeactivationReason(InvalidDeactivationReasonException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("INVALID_REASON", e.getMessage()));
    }
}
