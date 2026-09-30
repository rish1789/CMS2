package com.cms.booking.service;

import com.cms.booking.exception.ClinicNotAcceptingAppointmentsException;
import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.Booking;
import com.cms.booking.exception.AppointmentTypeNotFoundException;
import com.cms.booking.exception.ForbiddenException;
import com.cms.booking.exception.InvalidMobileNumberException;
import com.cms.booking.exception.PatientNotFoundException;
import com.cms.booking.exception.SessionNotAcceptingBookingsException;
import com.cms.booking.repository.AppointmentTypeRepository;
import com.cms.booking.repository.BookingRepository;


import com.cms.common.IndianMobileNumberValidator;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.patient.record.domain.Patient;
import com.cms.patient.record.repository.PatientRepository;
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
 * 022: the Queue-mode analog of 020's {@link StaffBookingService}. Unlike Fixed-Time
 * booking, there is no pre-existing Slot to look up - booking itself mints one via
 * {@link QueueSlotService#issueNextSlot}.
 *
 * <p>067-queue-token-issuance-race (research.md Decision 2): patient resolution, token issuance and
 * the booking run in one transaction, so a booking that fails leaves no token behind (FR-008) and
 * the session lock taken by {@link QueueSlotService} is held until the booking commits. This
 * supersedes 022 research.md's "deliberately NOT @Transactional" - the retry closure it protected
 * no longer exists.
 */
@Service
public class StaffQueueBookingService {

    private final SessionRepository sessionRepository;
    private final QueueSlotService queueSlotService;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final FeeResolutionService feeResolutionService;
    private final AppointmentTypeRepository appointmentTypeRepository;
    private final PatientRepository patientRepository;
    private final BookingRepository bookingRepository;
    private final IndianMobileNumberValidator mobileNumberValidator;
    private final SessionAvailabilityService sessionAvailabilityService;
    private final TransactionTemplate transactionTemplate;

    public StaffQueueBookingService(
            SessionRepository sessionRepository,
            QueueSlotService queueSlotService,
            RoleAssignmentRepository roleAssignmentRepository,
            FeeResolutionService feeResolutionService,
            AppointmentTypeRepository appointmentTypeRepository,
            PatientRepository patientRepository,
            BookingRepository bookingRepository,
            IndianMobileNumberValidator mobileNumberValidator,
            SessionAvailabilityService sessionAvailabilityService,
            PlatformTransactionManager transactionManager) {
        this.sessionRepository = sessionRepository;
        this.queueSlotService = queueSlotService;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.feeResolutionService = feeResolutionService;
        this.appointmentTypeRepository = appointmentTypeRepository;
        this.patientRepository = patientRepository;
        this.bookingRepository = bookingRepository;
        this.mobileNumberValidator = mobileNumberValidator;
        this.sessionAvailabilityService = sessionAvailabilityService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public Booking bookSlot(UUID callerAccountId, UUID clinicId, UUID sessionId, BookSlotInput input) {
        Session session = sessionRepository
                .findById(sessionId)
                .filter(s -> s.getClinic().getId().equals(clinicId))
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        requireAuthorized(callerAccountId, clinicId);
        // 062-rejected-clinic-gating (FR-001): after the existing role check, before any write.
        if (session.getClinic().isRejected()) {
            throw new ClinicNotAcceptingAppointmentsException(clinicId);
        }

        if (session.getMode() != ScheduleMode.QUEUE) {
            throw new NotAQueueSessionException(sessionId);
        }

        // 065-phase1-stabilization (research.md R4): a past or cancelled session takes no tokens.
        if (sessionAvailabilityService.evaluate(session, null) != Verdict.ACCEPTING) {
            throw new SessionNotAcceptingBookingsException(sessionId);
        }

        UUID doctorProfileId = session.getDoctorProfile().getId();

        // FR-005: the first real write-gate - nothing is written before this succeeds.
        BigDecimal lockedFee = feeResolutionService.resolve(doctorProfileId, input.appointmentTypeId());
        AppointmentType appointmentType = appointmentTypeRepository
                .findById(input.appointmentTypeId())
                .orElseThrow(() -> new AppointmentTypeNotFoundException(input.appointmentTypeId()));

        return transactionTemplate.execute(status -> {
            Patient patient = resolveOrCreatePatient(clinicId, session, input);
            // 067: joins this transaction and locks the session until the booking commits.
            Slot slot = queueSlotService.issueNextSlot(sessionId);
            return bookingRepository.save(new Booking(slot, patient, appointmentType, lockedFee, callerAccountId));
        });
    }

    private Patient resolveOrCreatePatient(UUID clinicId, Session session, BookSlotInput input) {
        if (input.patientId() != null) {
            return patientRepository
                    .findById(input.patientId())
                    .filter(p -> p.getClinic().getId().equals(clinicId))
                    .orElseThrow(() -> new PatientNotFoundException(input.patientId()));
        }

        if (!mobileNumberValidator.isValid(input.patientPhone())) {
            throw new InvalidMobileNumberException();
        }
        return patientRepository.save(new Patient(session.getClinic(), null, input.patientName(), input.patientPhone()));
    }

    /** FR-001: an active Operations or ClinicAdmin at this clinic - never the Doctor (mirrors 020). */
    private void requireAuthorized(UUID callerAccountId, UUID clinicId) {
        boolean isOperations = roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                callerAccountId, clinicId, RoleAssignment.Role.Operations);
        boolean isClinicAdmin = roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                callerAccountId, clinicId, RoleAssignment.Role.ClinicAdmin);

        if (!isOperations && !isClinicAdmin) {
            throw new ForbiddenException();
        }
    }

    public record BookSlotInput(UUID patientId, String patientName, String patientPhone, UUID appointmentTypeId) {}
}
