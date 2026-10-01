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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 022: the Queue-mode analog of 021's {@link PatientBookingService}.
 *
 * <p>067-queue-token-issuance-race (research.md Decisions 2-4): patient linking, token issuance and
 * the booking run in one transaction, so a booking that fails leaves no token behind (FR-008) and
 * the session lock taken by {@link QueueSlotService} is held until the booking commits. This
 * supersedes 022 research.md's "two separate atomic units". The 062 clinic check and the 060 gate
 * stay before and outside that transaction, as merged in #20.
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
    private final TransactionTemplate transactionTemplate;

    public PatientQueueBookingService(
            SessionRepository sessionRepository,
            QueueSlotService queueSlotService,
            FeeResolutionService feeResolutionService,
            AppointmentTypeRepository appointmentTypeRepository,
            PatientLinkingService patientLinkingService,
            BookingRepository bookingRepository,
            BookingProtectionService bookingProtectionService,
            ClinicRepository clinicRepository,
            SessionAvailabilityService sessionAvailabilityService,
            PlatformTransactionManager transactionManager) {
        this.sessionRepository = sessionRepository;
        this.queueSlotService = queueSlotService;
        this.feeResolutionService = feeResolutionService;
        this.appointmentTypeRepository = appointmentTypeRepository;
        this.patientLinkingService = patientLinkingService;
        this.bookingRepository = bookingRepository;
        this.bookingProtectionService = bookingProtectionService;
        this.clinicRepository = clinicRepository;
        this.sessionAvailabilityService = sessionAvailabilityService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
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
        // 067: the success flag commits with the booking; on failure the admitted row stays a
        // failure (060 FR-008) and the token is rolled back (067 FR-008).
        return transactionTemplate.execute(status -> {
            Booking booking = doBookSlot(patientAccountId, clinicId, sessionId, input);
            bookingProtectionService.recordSuccess(attemptId, booking);
            return booking;
        });
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

        BigDecimal lockedFee = feeResolutionService.resolve(clinicId, doctorProfileId, input.appointmentTypeId());
        AppointmentType appointmentType = appointmentTypeRepository
                .findById(input.appointmentTypeId())
                .orElseThrow(() -> new AppointmentTypeNotFoundException(input.appointmentTypeId()));

        Patient patient = patientLinkingService.findOrCreatePatient(patientAccountId, clinicId, input.patientName());

        // 067: joins this booking's transaction and locks the session until it commits. Lock order:
        // the patient account (findOrCreatePatient, 066) first, then the session.
        Slot slot = queueSlotService.issueNextSlot(sessionId);

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
