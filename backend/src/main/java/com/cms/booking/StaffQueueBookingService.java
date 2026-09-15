package com.cms.booking;

import com.cms.common.IndianMobileNumberValidator;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.patient.record.Patient;
import com.cms.patient.record.PatientRepository;
import com.cms.scheduling.NotAQueueSessionException;
import com.cms.scheduling.QueueSlotService;
import com.cms.scheduling.ScheduleMode;
import com.cms.scheduling.Session;
import com.cms.scheduling.SessionNotFoundException;
import com.cms.scheduling.SessionRepository;
import com.cms.scheduling.Slot;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 022: the Queue-mode analog of 020's {@link StaffBookingService}. Unlike Fixed-Time
 * booking, there is no pre-existing Slot to look up - booking itself mints one via
 * {@link QueueSlotService#issueNextSlot}. This top-level method is deliberately NOT
 * {@code @Transactional} (research.md): wrapping {@code issueNextSlot} in a broader
 * transaction here would silently defeat its own proven per-attempt-retry race closure,
 * the same class of bug already found and fixed twice this session (020, 021) - just
 * proactively avoided here instead of reactively fixed.
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

    public StaffQueueBookingService(
            SessionRepository sessionRepository,
            QueueSlotService queueSlotService,
            RoleAssignmentRepository roleAssignmentRepository,
            FeeResolutionService feeResolutionService,
            AppointmentTypeRepository appointmentTypeRepository,
            PatientRepository patientRepository,
            BookingRepository bookingRepository,
            IndianMobileNumberValidator mobileNumberValidator) {
        this.sessionRepository = sessionRepository;
        this.queueSlotService = queueSlotService;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.feeResolutionService = feeResolutionService;
        this.appointmentTypeRepository = appointmentTypeRepository;
        this.patientRepository = patientRepository;
        this.bookingRepository = bookingRepository;
        this.mobileNumberValidator = mobileNumberValidator;
    }

    public Booking bookSlot(UUID callerAccountId, UUID clinicId, UUID sessionId, BookSlotInput input) {
        Session session = sessionRepository
                .findById(sessionId)
                .filter(s -> s.getClinic().getId().equals(clinicId))
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        requireAuthorized(callerAccountId, clinicId);

        if (session.getMode() != ScheduleMode.QUEUE) {
            throw new NotAQueueSessionException(sessionId);
        }

        UUID doctorProfileId = session.getDoctorProfile().getId();

        // FR-005: the first real write-gate - nothing is written before this succeeds.
        BigDecimal lockedFee = feeResolutionService.resolve(doctorProfileId, input.appointmentTypeId());
        AppointmentType appointmentType = appointmentTypeRepository
                .findById(input.appointmentTypeId())
                .orElseThrow(() -> new AppointmentTypeNotFoundException(input.appointmentTypeId()));

        Patient patient = resolveOrCreatePatient(clinicId, session, input);

        // Called outside any transaction this method opens (research.md).
        Slot slot = queueSlotService.issueNextSlot(sessionId);

        // Convergence fix: no separate @Transactional helper here - self-invocation from
        // this method would have silently bypassed it anyway (the same gotcha research.md
        // documents for QueueSlotService.attemptIssueSlot). A detached Slot reference is
        // fine for this simple, non-cascading INSERT (research.md); bookingRepository.save
        // is independently atomic on its own via Spring Data JPA. No race-closure catch
        // needed either: this Slot was just freshly minted, so no other caller can already
        // hold a reference to race against (research.md).
        return bookingRepository.save(new Booking(slot, patient, appointmentType, lockedFee, callerAccountId));
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
