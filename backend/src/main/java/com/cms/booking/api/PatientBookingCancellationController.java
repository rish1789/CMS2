package com.cms.booking.api;

import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingCancellationReason;
import com.cms.booking.exception.BookingNotFoundException;
import com.cms.booking.exception.CancellationCutoffPassedException;
import com.cms.booking.exception.CancellationReasonRequiredException;
import com.cms.booking.exception.WalkInNotSelfCancellableException;
import com.cms.booking.exception.InvalidCancellationReasonException;
import com.cms.booking.repository.BookingRepository;
import com.cms.booking.service.BookingCancellationService;
import com.cms.booking.service.PatientVisitOutcomes;


import com.cms.booking.dto.BookingResponse;
import com.cms.booking.dto.CancelBookingRequest;
import com.cms.patient.account.config.SecurityConfig;
import com.cms.scheduling.exception.NotAFixedTimeSessionException;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 028: ownership of the Booking (not clinic membership) is the access boundary, and the 2-hour cutoff applies only here, never to staff (research.md R4/R6). */
@RestController
public class PatientBookingCancellationController {

    private final BookingRepository bookingRepository;
    private final BookingCancellationService bookingCancellationService;
    private final PatientVisitOutcomes patientVisitOutcomes;

    public PatientBookingCancellationController(
            BookingRepository bookingRepository,
            BookingCancellationService bookingCancellationService,
            PatientVisitOutcomes patientVisitOutcomes) {
        this.bookingRepository = bookingRepository;
        this.bookingCancellationService = bookingCancellationService;
        this.patientVisitOutcomes = patientVisitOutcomes;
    }

    /**
     * patient-cancellation-reason: {@code request} is {@code required = false} deliberately -
     * an entirely missing body must fail through this controller's own {@link
     * CancellationReasonRequiredException} (a clean, typed 400 with its own error code), not
     * Spring's generic "Required request body is missing" - mirrors {@code
     * ClinicVerificationService}/{@code RejectRequest}'s identical precedent for the admin
     * verification queues: reason validation lives in application code, not Bean Validation.
     */
    @PostMapping("/api/v1/patients/bookings/{bookingId}/cancel")
    public BookingResponse cancel(
            @PathVariable UUID bookingId,
            @Valid @RequestBody(required = false) CancelBookingRequest request,
            Authentication authentication) {
        UUID patientAccountId = SecurityConfig.currentPatientAccountId(authentication);

        Booking booking = bookingRepository
                .findById(bookingId)
                .filter(b -> b.getPatient().getPatientAccount() != null
                        && b.getPatient().getPatientAccount().getId().equals(patientAccountId))
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        var slot = booking.getSlot();

        // 069-patient-visit-outcomes FR-005: the same checks, in the same order, now shared with the
        // eligibility shown to the patient - Queue-mode first (its startTime is null, so the cutoff
        // must not run first), then an untimed walk-in (063 FR-015), then the 2-hour cutoff (028
        // FR-002). Only the patient path is cutoff-gated; staff never are. Booking and slot state are
        // still enforced by BookingCancellationService.cancel below.
        var refusal = patientVisitOutcomes.requestRefusal(slot);
        if (refusal.isPresent()) {
            switch (refusal.get()) {
                case QUEUE_BOOKING -> throw new NotAFixedTimeSessionException(slot.getSession().getId());
                case WALK_IN -> throw new WalkInNotSelfCancellableException(bookingId);
                case CUTOFF_PASSED -> throw new CancellationCutoffPassedException(bookingId);
                default -> throw new IllegalStateException("Not a request-time refusal: " + refusal.get());
            }
        }

        String rawReason = request == null ? null : request.reason();
        if (rawReason == null || rawReason.isBlank()) {
            throw new CancellationReasonRequiredException(bookingId);
        }
        BookingCancellationReason reason;
        try {
            reason = BookingCancellationReason.valueOf(rawReason);
        } catch (IllegalArgumentException e) {
            throw new InvalidCancellationReasonException(rawReason);
        }
        // 062-rejected-clinic-gating: CLINIC_REJECTED is system-only - set solely by the rejection
        // cascade, never something a patient can claim as their own reason.
        if (reason == BookingCancellationReason.CLINIC_REJECTED) {
            throw new InvalidCancellationReasonException(rawReason);
        }

        return BookingResponse.of(bookingCancellationService.cancel(booking, reason, request.reasonDetail()));
    }
}
