package com.cms.booking;

import com.cms.booking.dto.BookingResponse;
import com.cms.booking.dto.CancelBookingRequest;
import com.cms.patient.account.SecurityConfig;
import com.cms.scheduling.NotAFixedTimeSessionException;
import com.cms.scheduling.ScheduleMode;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 028: ownership of the Booking (not clinic membership) is the access boundary, and the 2-hour cutoff applies only here, never to staff (research.md R4/R6). */
@RestController
public class PatientBookingCancellationController {

    private static final int CUTOFF_HOURS = 2;

    private final BookingRepository bookingRepository;
    private final BookingCancellationService bookingCancellationService;

    public PatientBookingCancellationController(
            BookingRepository bookingRepository, BookingCancellationService bookingCancellationService) {
        this.bookingRepository = bookingRepository;
        this.bookingCancellationService = bookingCancellationService;
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

        // Checked before the cutoff computation below: a Queue-mode Slot's startTime is null
        // (013/019), so combining it into a LocalDateTime would NPE if this ran first. Reuses
        // 025/026/027's identical exception - FR-009 is Fixed-Time-only for both endpoints,
        // BookingCancellationService.cancel enforces it too, but the cutoff check here needs
        // it enforced earlier still.
        if (slot.getSession().getMode() != ScheduleMode.FIXED_TIME) {
            throw new NotAFixedTimeSessionException(slot.getSession().getId());
        }

        // FR-002/research.md R6: reuses 023's Session/Slot-time-combining technique. Only the
        // patient path is cutoff-gated - staff (StaffBookingCancellationController) never checks this.
        LocalDateTime scheduledAt = LocalDateTime.of(slot.getSession().getSessionDate(), slot.getStartTime());
        if (scheduledAt.isBefore(LocalDateTime.now().plusHours(CUTOFF_HOURS))) {
            throw new CancellationCutoffPassedException(bookingId);
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

        return BookingResponse.of(bookingCancellationService.cancel(booking, reason, request.reasonDetail()));
    }
}
