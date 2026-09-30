package com.cms.booking.service;

import com.cms.booking.exception.ClinicNotAcceptingAppointmentsException;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.Booking;
import com.cms.booking.exception.AppointmentTypeNotFoundException;
import com.cms.booking.exception.SessionNotAcceptingBookingsException;
import com.cms.booking.repository.AppointmentTypeRepository;
import com.cms.booking.repository.BookingRepository;


import com.cms.patient.record.domain.Patient;
import com.cms.patient.record.service.PatientLinkingService;
import com.cms.scheduling.exception.NotAQueueSessionException;
import com.cms.scheduling.service.QueueSlotService;
import com.cms.scheduling.service.SessionAvailabilityService;
import com.cms.scheduling.service.SessionAvailabilityService.Verdict;
import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.exception.SessionNotFoundException;
import com.cms.scheduling.repository.SessionRepository;
import com.cms.scheduling.domain.Slot;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 022: the Queue-mode analog of 021's {@link PatientBookingService}. Same non-{@code
 * @Transactional} top-level calling convention as {@link StaffQueueBookingService} - see
 * its javadoc and research.md.
 */
@Service
public class PatientQueueBookingService {

    private final SessionRepository sessionRepository;
    private final QueueSlotService queueSlotService;
    private final FeeResolutionService feeResolutionService;
    private final AppointmentTypeRepository appointmentTypeRepository;
    private final PatientLinkingService patientLinkingService;
    private final BookingRepository bookingRepository;
    private final BookingProtectionService bookingProtectionService;
    private final ClinicRepository clinicRepository;
    private final SessionAvailabilityService sessionAvailabilityService;

    public PatientQueueBookingService(
            SessionRepository sessionRepository,
            QueueSlotService queueSlotService,
            FeeResolutionService feeResolutionService,
            AppointmentTypeRepository appointmentTypeRepository,
            PatientLinkingService patientLinkingService,
            BookingRepository bookingRepository,
            BookingProtectionService bookingProtectionService,
            ClinicRepository clinicRepository,
            SessionAvailabilityService sessionAvailabilityService) {
        this.sessionRepository = sessionRepository;
        this.queueSlotService = queueSlotService;
        this.feeResolutionService = feeResolutionService;
        this.appointmentTypeRepository = appointmentTypeRepository;
        this.patientLinkingService = patientLinkingService;
        this.bookingRepository = bookingRepository;
        this.bookingProtectionService = bookingProtectionService;
        this.clinicRepository = clinicRepository;
        this.sessionAvailabilityService = sessionAvailabilityService;
    }

    public Booking bookSlot(UUID patientAccountId, UUID clinicId, UUID sessionId, BookSlotInput input) {
        // 062-rejected-clinic-gating (research.md Decision 1, "Ordering"): refused before the 060
        // rate-limit gate below, so an attempt at a rejected clinic is never recorded as booking
        // activity (which would also block that clinic's guarded permanent-delete).
        requireClinicAcceptingAppointments(clinicId);
        // 060-booking-abuse-prevention: same fixed gate order as PatientBookingService.bookSlot
        // (research.md Decision 6) - first thing this method does.
        // The gate runs in its own transaction here, so a rejection rolls back and is recorded
        // after it; an admitted attempt's row is committed before the booking below runs, so a
        // failure there is already counted (FR-008).
        UUID attemptId;
        try {
            attemptId = bookingProtectionService.checkAndRecordAttempt(patientAccountId, clinicId);
        } catch (RuntimeException e) {
            bookingProtectionService.recordAfterRollback(patientAccountId, clinicId, e);
            throw e;
        }
        Booking booking = doBookSlot(patientAccountId, clinicId, sessionId, input);
        bookingProtectionService.recordSuccess(attemptId, booking);
        return booking;
    }

    private Booking doBookSlot(UUID patientAccountId, UUID clinicId, UUID sessionId, BookSlotInput input) {
        Session session = sessionRepository
                .findById(sessionId)
                .filter(s -> s.getClinic().getId().equals(clinicId))
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (session.getMode() != ScheduleMode.QUEUE) {
            throw new NotAQueueSessionException(sessionId);
        }

        // 065-phase1-stabilization (research.md R4): a past or cancelled session takes no tokens.
        if (sessionAvailabilityService.evaluate(session, null) != Verdict.ACCEPTING) {
            throw new SessionNotAcceptingBookingsException(sessionId);
        }

        UUID doctorProfileId = session.getDoctorProfile().getId();

        BigDecimal lockedFee = feeResolutionService.resolve(doctorProfileId, input.appointmentTypeId());
        AppointmentType appointmentType = appointmentTypeRepository
                .findById(input.appointmentTypeId())
                .orElseThrow(() -> new AppointmentTypeNotFoundException(input.appointmentTypeId()));

        Patient patient = patientLinkingService.findOrCreatePatient(patientAccountId, clinicId, input.patientName());

        // Called outside any transaction this method opens (research.md).
        Slot slot = queueSlotService.issueNextSlot(sessionId);

        // Convergence fix: no separate @Transactional helper here - self-invocation from
        // this method would have silently bypassed it anyway (the same gotcha research.md
        // documents for QueueSlotService.attemptIssueSlot). A detached Slot reference is
        // fine for this simple, non-cascading INSERT (research.md); bookingRepository.save
        // is independently atomic on its own via Spring Data JPA.
        // _diagnostics CRITICAL fix: patientAccountId is a patient_account.id, never an
        // account.id - Booking.bookedByPatient sets the correct disjoint-identity column.
        return bookingRepository.save(Booking.bookedByPatient(slot, patient, appointmentType, lockedFee, patientAccountId));
    }

    /** 062-rejected-clinic-gating (FR-001): a rejected clinic takes no appointments until restored. */
    private void requireClinicAcceptingAppointments(UUID clinicId) {
        if (clinicRepository.findById(clinicId).map(Clinic::isRejected).orElse(false)) {
            throw new ClinicNotAcceptingAppointmentsException(clinicId);
        }
    }

    public record BookSlotInput(String patientName, UUID appointmentTypeId) {}
}
