package com.cms.identity.admin;

import com.cms.identity.api.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps admin-module domain exceptions to contracts/clinic-verification.md's error shapes. */
@RestControllerAdvice(basePackages = "com.cms.identity.admin")
public class AdminExceptionHandler {

    @ExceptionHandler(ClinicNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleClinicNotFound(ClinicNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of("CLINIC_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(DoctorProfileNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDoctorProfileNotFound(DoctorProfileNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("DOCTOR_PROFILE_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(DuplicateLicenseNumberException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateLicenseNumber(DuplicateLicenseNumberException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        "DUPLICATE_LICENSE_NUMBER", "This license number is already on file for a different doctor"));
    }

    @ExceptionHandler(MissingRejectionReasonException.class)
    public ResponseEntity<ErrorResponse> handleMissingRejectionReason(MissingRejectionReasonException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("MISSING_REASON", e.getMessage()));
    }

    @ExceptionHandler(InvalidRejectionReasonException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRejectionReason(InvalidRejectionReasonException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("INVALID_REASON", e.getMessage()));
    }

    @ExceptionHandler(CannotRejectVerifiedException.class)
    public ResponseEntity<ErrorResponse> handleCannotRejectVerified(CannotRejectVerifiedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("ALREADY_VERIFIED", e.getMessage()));
    }

    @ExceptionHandler(InvalidListStatusException.class)
    public ResponseEntity<ErrorResponse> handleInvalidListStatus(InvalidListStatusException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("INVALID_STATUS", e.getMessage()));
    }

    @ExceptionHandler(InvalidSortFieldException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSortField(InvalidSortFieldException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("INVALID_SORT", e.getMessage()));
    }

    @ExceptionHandler(CannotDeleteUnlessRejectedException.class)
    public ResponseEntity<ErrorResponse> handleCannotDeleteUnlessRejected(CannotDeleteUnlessRejectedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("NOT_REJECTED", e.getMessage()));
    }

    @ExceptionHandler(DeletionBlockedException.class)
    public ResponseEntity<ErrorResponse> handleDeletionBlocked(DeletionBlockedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("DELETION_BLOCKED", e.getMessage()));
    }
}
