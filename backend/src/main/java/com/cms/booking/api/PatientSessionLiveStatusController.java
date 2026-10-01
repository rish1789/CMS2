package com.cms.booking.api;

import com.cms.booking.domain.Booking;
import com.cms.booking.domain.VisitOutcome;
import com.cms.booking.service.PatientVisitOutcomes;
import com.cms.booking.dto.PatientSessionLiveStatusResponse;
import com.cms.booking.exception.BookingNotFoundException;
import com.cms.booking.repository.BookingRepository;
import com.cms.patient.account.config.SecurityConfig;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.service.SessionLiveStatusService;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 061-doctor-live-status (research.md Decision 5): mirrors {@link PatientQueuePositionController}
 * exactly - ownership of the Booking, not clinic membership, is the access boundary (no clinicId
 * in the path). Delegates the underlying calculation to {@link SessionLiveStatusService}
 * (one-way {@code booking -> scheduling} call, the same direction {@link Booking#getSlot()}
 * already establishes), then trims the result down to the patient-safe shape (FR-004/FR-011) -
 * no raw status code, no other patient's data.
 */
@RestController
public class PatientSessionLiveStatusController {

    private final BookingRepository bookingRepository;
    private final SessionLiveStatusService sessionLiveStatusService;
    private final PatientVisitOutcomes patientVisitOutcomes;

    public PatientSessionLiveStatusController(
            BookingRepository bookingRepository,
            SessionLiveStatusService sessionLiveStatusService,
            PatientVisitOutcomes patientVisitOutcomes) {
        this.bookingRepository = bookingRepository;
        this.sessionLiveStatusService = sessionLiveStatusService;
        this.patientVisitOutcomes = patientVisitOutcomes;
    }

    @GetMapping("/api/v1/patients/bookings/{bookingId}/live-status")
    public PatientSessionLiveStatusResponse liveStatus(@PathVariable UUID bookingId, Authentication authentication) {
        UUID patientAccountId = SecurityConfig.currentPatientAccountId(authentication);

        Booking booking = bookingRepository
                .findById(bookingId)
                .filter(b -> b.getPatient().getPatientAccount() != null
                        && b.getPatient().getPatientAccount().getId().equals(patientAccountId))
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        Slot patientSlot = booking.getSlot();
        Session session = patientSlot.getSession();
        SessionLiveStatusService.LiveStatus status = sessionLiveStatusService.liveStatusFor(session);
        VisitOutcome visitOutcome = patientVisitOutcomes.outcome(booking);

        if (!status.applicable()) {
            return new PatientSessionLiveStatusResponse(bookingId, false, null, null, null, null, visitOutcome);
        }

        String doctorName = session.getDoctorProfile().getAccount().getName();
        Integer estimatedWaitMinutes = sessionLiveStatusService.estimatedWaitMinutesFor(session, patientSlot, status);
        return new PatientSessionLiveStatusResponse(
                bookingId,
                true,
                doctorName,
                status.currentPatientOrdinal(),
                statusText(status, visitOutcome),
                estimatedWaitMinutes,
                visitOutcome);
    }

    /**
     * FR-004/FR-011: plain language only - never one of {@link SessionLiveStatusService.Status}'s raw codes.
     *
     * <p>069-patient-visit-outcomes FR-004 (live-audit finding 1): once the patient's own visit is
     * resolved, the text describes that visit, not the session. A session is "complete" when every
     * slot is resolved - no-shows included - so it never established that this patient was seen.
     */
    private String statusText(SessionLiveStatusService.LiveStatus status, VisitOutcome visitOutcome) {
        switch (visitOutcome) {
            case COMPLETED:
                return "Visit complete";
            case NO_SHOW:
                return "Missed appointment";
            case CANCELLED:
                return "Booking cancelled";
            default:
                break;
        }
        Integer deviation = status.deviationMinutes();
        return switch (status.status()) {
            case NOT_STARTED -> "Not started yet";
            case ON_TIME -> "On time";
            case DELAYED -> deviation == null ? "Delayed" : deviation + " min delayed";
            case RUNNING_EARLY -> deviation == null ? "Running early" : "Running " + deviation + " min early";
            // The session finished but this patient's own visit is not recorded as resolved.
            case COMPLETED -> "Session finished";
        };
    }
}
