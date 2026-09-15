package com.cms.booking;

import com.cms.identity.api.dto.ErrorResponse;
import com.cms.scheduling.NotAFixedTimeSessionException;
import com.cms.scheduling.NotAQueueSessionException;
import com.cms.scheduling.SessionNotFoundException;
import com.cms.scheduling.TokenIssuanceFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps this feature's exception types to contracts/fee-resolution.md's error shapes. */
@RestControllerAdvice
public class BookingExceptionHandler {

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("FORBIDDEN", e.getMessage()));
    }

    /** 041-staff-console-pickers: distinct from {@link ForbiddenException} - see that exception's own message. */
    @ExceptionHandler(NotStaffedAtClinicException.class)
    public ResponseEntity<ErrorResponse> handleNotStaffedAtClinic(NotStaffedAtClinicException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("FORBIDDEN", e.getMessage()));
    }

    @ExceptionHandler(DoctorProfileNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDoctorProfileNotFound(DoctorProfileNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("DOCTOR_PROFILE_NOT_FOUND", e.getMessage()));
    }

    /**
     * 020: the first HTTP-reachable caller of {@link FeeResolutionService} - neither of
     * its exceptions had a mapping before now, since 015 itself had no HTTP endpoint.
     */
    @ExceptionHandler(AppointmentTypeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAppointmentTypeNotFound(AppointmentTypeNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("APPOINTMENT_TYPE_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(NoFeeConfiguredException.class)
    public ResponseEntity<ErrorResponse> handleNoFeeConfigured(NoFeeConfiguredException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("NO_FEE_CONFIGURED", e.getMessage()));
    }

    @ExceptionHandler(SlotNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSlotNotFound(SlotNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of("SLOT_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(SlotAlreadyBookedException.class)
    public ResponseEntity<ErrorResponse> handleSlotAlreadyBooked(SlotAlreadyBookedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("SLOT_ALREADY_BOOKED", e.getMessage()));
    }

    /** patient-slot-booking-date-logic: a stale client tried to book a Slot whose day has already passed. */
    @ExceptionHandler(SlotDateInThePastException.class)
    public ResponseEntity<ErrorResponse> handleSlotDateInThePast(SlotDateInThePastException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("SLOT_DATE_IN_THE_PAST", e.getMessage()));
    }

    @ExceptionHandler(PatientNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePatientNotFound(PatientNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of("PATIENT_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(InvalidMobileNumberException.class)
    public ResponseEntity<ErrorResponse> handleInvalidMobileNumber(InvalidMobileNumberException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("INVALID_MOBILE_NUMBER", e.getMessage()));
    }

    /**
     * 022: the first HTTP-reachable caller of {@code QueueSlotService} - none of these
     * three exceptions (013/019) had a mapping before now, the same gap class 020 found
     * for FeeResolutionService's own exceptions.
     */
    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSessionNotFound(SessionNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of("SESSION_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(NotAQueueSessionException.class)
    public ResponseEntity<ErrorResponse> handleNotAQueueSession(NotAQueueSessionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("NOT_A_QUEUE_SESSION", e.getMessage()));
    }

    /** 503, not 409: transient exhaustion under load is retry-later, not a client-correctable conflict (research.md). */
    @ExceptionHandler(TokenIssuanceFailedException.class)
    public ResponseEntity<ErrorResponse> handleTokenIssuanceFailed(TokenIssuanceFailedException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of("TOKEN_ISSUANCE_FAILED", e.getMessage()));
    }

    /** 025: no Slot qualifies at any priority tier for this Session. */
    @ExceptionHandler(NoSlotAvailableException.class)
    public ResponseEntity<ErrorResponse> handleNoSlotAvailable(NoSlotAvailableException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("NO_SLOT_AVAILABLE", e.getMessage()));
    }

    /** 025: a priority-(3) insertion was attempted without a required override reason. */
    @ExceptionHandler(OverrideReasonRequiredException.class)
    public ResponseEntity<ErrorResponse> handleOverrideReasonRequired(OverrideReasonRequiredException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("OVERRIDE_REASON_REQUIRED", e.getMessage()));
    }

    /** 025 (convergence): walk-in priority insertion is Fixed-Time-only (spec Assumptions/Edge Cases). */
    @ExceptionHandler(NotAFixedTimeSessionException.class)
    public ResponseEntity<ErrorResponse> handleNotAFixedTimeSession(NotAFixedTimeSessionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("NOT_A_FIXED_TIME_SESSION", e.getMessage()));
    }

    /** 027: no Booking with this id, or it doesn't belong to the requesting clinic/patient. */
    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBookingNotFound(BookingNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of("BOOKING_NOT_FOUND", e.getMessage()));
    }

    /** 028: the Booking is already cancelled, or its Slot has already reached a resolved outcome. */
    @ExceptionHandler(BookingNotCancellableException.class)
    public ResponseEntity<ErrorResponse> handleBookingNotCancellable(BookingNotCancellableException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("BOOKING_NOT_CANCELLABLE", e.getMessage()));
    }

    /** 028: a patient's self-service cancellation attempt was made less than 2 hours before the scheduled slot time. */
    @ExceptionHandler(CancellationCutoffPassedException.class)
    public ResponseEntity<ErrorResponse> handleCancellationCutoffPassed(CancellationCutoffPassedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("CANCELLATION_CUTOFF_PASSED", e.getMessage()));
    }

    /** patient-cancellation-reason: a patient self-service cancellation with no reason at all. */
    @ExceptionHandler(CancellationReasonRequiredException.class)
    public ResponseEntity<ErrorResponse> handleCancellationReasonRequired(CancellationReasonRequiredException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("CANCELLATION_REASON_REQUIRED", e.getMessage()));
    }

    /** patient-cancellation-reason: the {@code reason} value didn't match any {@code BookingCancellationReason} literal. */
    @ExceptionHandler(InvalidCancellationReasonException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCancellationReason(InvalidCancellationReasonException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("INVALID_CANCELLATION_REASON", e.getMessage()));
    }

    /** 029: the Session has zero currently-active Bookings - already cancelled, or it never had any. */
    @ExceptionHandler(SessionAlreadyCancelledException.class)
    public ResponseEntity<ErrorResponse> handleSessionAlreadyCancelled(SessionAlreadyCancelledException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("SESSION_ALREADY_CANCELLED", e.getMessage()));
    }

    /** 030 (bounded range extension): an optional {@code toTime} that isn't strictly after {@code cutoffTime}. */
    @ExceptionHandler(InvalidCancellationRangeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCancellationRange(InvalidCancellationRangeException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("INVALID_CANCELLATION_RANGE", e.getMessage()));
    }
}
