package com.cms.scheduling;

import com.cms.identity.api.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps this feature's exception types to contracts/schedule.md's error shapes. */
@RestControllerAdvice
public class ScheduleExceptionHandler {

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("FORBIDDEN", e.getMessage()));
    }

    /** 041-staff-console-pickers: distinct from {@link ForbiddenException} - see that exception's own message. */
    @ExceptionHandler(NotStaffedAtClinicException.class)
    public ResponseEntity<ErrorResponse> handleNotStaffedAtClinic(NotStaffedAtClinicException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("FORBIDDEN", e.getMessage()));
    }

    @ExceptionHandler(ClinicNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleClinicNotFound(ClinicNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of("CLINIC_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(DoctorProfileNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDoctorProfileNotFound(DoctorProfileNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("DOCTOR_PROFILE_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(DoctorNotStaffedAtClinicException.class)
    public ResponseEntity<ErrorResponse> handleDoctorNotStaffedAtClinic(DoctorNotStaffedAtClinicException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("DOCTOR_NOT_STAFFED_AT_CLINIC", e.getMessage()));
    }

    @ExceptionHandler(InvalidScheduleException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSchedule(InvalidScheduleException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("INVALID_SCHEDULE", e.getMessage()));
    }

    @ExceptionHandler(ScheduleOverlapException.class)
    public ResponseEntity<ErrorResponse> handleScheduleOverlap(ScheduleOverlapException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("SCHEDULE_OVERLAP", e.getMessage()));
    }

    @ExceptionHandler(ScheduleNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleScheduleNotFound(ScheduleNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of("SCHEDULE_NOT_FOUND", e.getMessage()));
    }

    /** 026: distinct from com.cms.booking.SlotNotFoundException (016) - a different module's own exception of the same name. */
    @ExceptionHandler(SlotNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSlotNotFound(SlotNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of("SLOT_NOT_FOUND", e.getMessage()));
    }

    /** 026: the Slot's status is not BOOKED - still OPEN, or already COMPLETED. */
    @ExceptionHandler(SlotNotCompletableException.class)
    public ResponseEntity<ErrorResponse> handleSlotNotCompletable(SlotNotCompletableException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("SLOT_NOT_COMPLETABLE", e.getMessage()));
    }
}
