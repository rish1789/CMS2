package com.cms.identity.staff.exception;



import com.cms.identity.account.exception.InvalidCredentialsException;
import com.cms.identity.account.exception.NoActiveClinicAccessException;
import com.cms.identity.account.exception.StaffClinicNotActiveException;
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

    /** 075-login-hardening (D-3C-2): unknown identifier and wrong password get this one answer. */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("INVALID_CREDENTIALS", e.getMessage()));
    }

    /** 075-login-hardening (D-3C-1): the right password, but no active role at any clinic. */
    @ExceptionHandler(NoActiveClinicAccessException.class)
    public ResponseEntity<ErrorResponse> handleNoActiveClinicAccess(NoActiveClinicAccessException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("NO_ACTIVE_CLINIC_ACCESS", e.getMessage()));
    }

    /** 062-rejected-clinic-gating (FR-007): every role this account holds is Doctor/Operations at a rejected clinic. */
    @ExceptionHandler(StaffClinicNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleStaffClinicNotActive(StaffClinicNotActiveException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("CLINIC_NOT_ACTIVE", e.getMessage()));
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

    /** real-bug-fix 2026-09-17: StaffPasswordResetService.setPassword's own password-policy check. */
    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPassword(InvalidPasswordException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.withFailedRules("INVALID_PASSWORD", e.getMessage(), e.getFailedRules()));
    }
}
