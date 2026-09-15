package com.cms.clinical;

import com.cms.identity.api.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps this feature's own new exceptions to contracts/consultation-note.md's error shapes.
 * {@code com.cms.booking.BookingNotFoundException} is reused directly and is already globally
 * mapped elsewhere - no mapping needed here.
 */
@RestControllerAdvice
public class ClinicalDocumentationExceptionHandler {

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("FORBIDDEN", e.getMessage()));
    }

    @ExceptionHandler(ConsultationNoteAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyExists(ConsultationNoteAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("CONSULTATION_NOTE_ALREADY_EXISTS", e.getMessage()));
    }

    @ExceptionHandler(ConsultationNoteNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ConsultationNoteNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("CONSULTATION_NOTE_NOT_FOUND", e.getMessage()));
    }

    /** 035: a Prescription was submitted with zero Items. */
    @ExceptionHandler(PrescriptionItemRequiredException.class)
    public ResponseEntity<ErrorResponse> handlePrescriptionItemRequired(PrescriptionItemRequiredException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("PRESCRIPTION_ITEM_REQUIRED", e.getMessage()));
    }
}
