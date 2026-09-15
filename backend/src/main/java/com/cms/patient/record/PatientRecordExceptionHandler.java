package com.cms.patient.record;

import com.cms.identity.api.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps this feature's own new exceptions to contracts/patient-anonymization.md's error shapes. */
@RestControllerAdvice
public class PatientRecordExceptionHandler {

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("FORBIDDEN", e.getMessage()));
    }

    /** 041-staff-console-pickers: distinct from {@link ForbiddenException} - see that exception's own message. */
    @ExceptionHandler(NotStaffedAtClinicException.class)
    public ResponseEntity<ErrorResponse> handleNotStaffedAtClinic(NotStaffedAtClinicException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("FORBIDDEN", e.getMessage()));
    }

    @ExceptionHandler(PatientNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePatientNotFound(PatientNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of("PATIENT_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(PatientHasActiveFutureBookingException.class)
    public ResponseEntity<ErrorResponse> handlePatientHasActiveFutureBooking(PatientHasActiveFutureBookingException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("PATIENT_HAS_ACTIVE_FUTURE_BOOKING", e.getMessage()));
    }

    @ExceptionHandler(InvalidSearchTermException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSearchTerm(InvalidSearchTermException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("INVALID_SEARCH_TERM", e.getMessage()));
    }
}
