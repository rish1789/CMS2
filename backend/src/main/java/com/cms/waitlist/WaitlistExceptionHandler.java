package com.cms.waitlist;

import com.cms.identity.api.dto.ErrorResponse;
import com.cms.notification.PatientAccountNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps this feature's own new exceptions to contracts/waitlist-join.md's error shapes.
 * {@code ClinicNotFoundException} and {@code DoctorNotStaffedAtClinicException} are reused
 * directly from {@code com.cms.scheduling} and are already globally mapped by {@code
 * ScheduleExceptionHandler} (013) - no mapping needed here. {@link PatientAccountNotFoundException}
 * (011) is reused too, but this is its first HTTP-reachable caller, so its mapping is added here
 * (the same gap class 020 hit for {@code FeeResolutionService}'s exceptions).
 */
@RestControllerAdvice
public class WaitlistExceptionHandler {

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("FORBIDDEN", e.getMessage()));
    }

    @ExceptionHandler(PatientAccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePatientAccountNotFound(PatientAccountNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("PATIENT_ACCOUNT_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(WaitlistTargetRequiredException.class)
    public ResponseEntity<ErrorResponse> handleWaitlistTargetRequired(WaitlistTargetRequiredException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("WAITLIST_TARGET_REQUIRED", e.getMessage()));
    }

    /** 032: no entry with this id, or it doesn't belong to the caller. */
    @ExceptionHandler(WaitlistEntryNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleWaitlistEntryNotFound(WaitlistEntryNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("WAITLIST_ENTRY_NOT_FOUND", e.getMessage()));
    }

    /** 032: the entry is not currently OFFERED, or its window has already lapsed. */
    @ExceptionHandler(WaitlistOfferNotClaimableException.class)
    public ResponseEntity<ErrorResponse> handleWaitlistOfferNotClaimable(WaitlistOfferNotClaimableException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("WAITLIST_OFFER_NOT_CLAIMABLE", e.getMessage()));
    }
}
