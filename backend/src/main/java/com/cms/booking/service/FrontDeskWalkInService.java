package com.cms.booking.service;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingStatus;
import com.cms.booking.domain.VisitReason;
import com.cms.booking.exception.AppointmentTypeNotFoundException;
import com.cms.booking.exception.ClinicNotAcceptingAppointmentsException;
import com.cms.booking.exception.DuplicateWalkInException;
import com.cms.booking.exception.ForbiddenException;
import com.cms.booking.exception.InvalidEmailException;
import com.cms.booking.exception.InvalidMobileNumberException;
import com.cms.booking.exception.PatientNotFoundException;
import com.cms.booking.exception.PatientRequiredException;
import com.cms.booking.exception.SessionNotAcceptingBookingsException;
import com.cms.booking.exception.VisitReasonDetailRequiredException;
import com.cms.booking.exception.VisitReasonRequiredException;
import com.cms.booking.repository.AppointmentTypeRepository;
import com.cms.booking.repository.BookingRepository;
import com.cms.common.IndianMobileNumberValidator;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.inbox.service.InboxItemService;
import com.cms.patient.record.domain.Patient;
import com.cms.patient.record.repository.PatientRepository;
import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.exception.SessionNotFoundException;
import com.cms.scheduling.repository.SessionRepository;
import com.cms.scheduling.service.QueueSlotService;
import com.cms.scheduling.service.SessionAvailabilityService;
import com.cms.scheduling.service.SessionAvailabilityService.Verdict;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 063-front-desk-walk-in (research.md Decision 4): the one front-desk walk-in registration, for both
 * session modes. It composes existing collaborators rather than duplicating them - the token
 * issuance ({@link QueueSlotService}), fee locking ({@link FeeResolutionService}), queue position
 * ({@link QueuePositionService}) and the inbox walk-in item ({@link InboxItemService}).
 *
 * <p>Fixed-Time: the walk-in joins the session's untimed walk-in line (W1, W2...) and never touches
 * a timed slot or a booked appointment (FR-008, SC-003); the walk-in's slot is BOOKED so the existing
 * Appeared ("Send in") and Complete actions accept it. Queue: the walk-in takes the next token in the
 * same line as booked patients, left OPEN exactly like every other queue token (FR-009).
 *
 * <p>Replaces the retired 025/058 walk-in slot insertion by explicit product decision (spec "Product
 * Decisions" section 5): a walk-in no longer takes a no-show's slot or an open timed slot.
 */
@Service
public class FrontDeskWalkInService {

    private static final int VISIT_REASON_DETAIL_MAX = 200;
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final SessionRepository sessionRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final FeeResolutionService feeResolutionService;
    private final AppointmentTypeRepository appointmentTypeRepository;
    private final PatientRepository patientRepository;
    private final WalkInPatientRegistrar walkInPatientRegistrar;
    private final BookingRepository bookingRepository;
    private final IndianMobileNumberValidator mobileNumberValidator;
    private final QueueSlotService queueSlotService;
    private final QueuePositionService queuePositionService;
    private final InboxItemService inboxItemService;
    private final SessionAvailabilityService sessionAvailabilityService;

    public FrontDeskWalkInService(
            SessionRepository sessionRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            FeeResolutionService feeResolutionService,
            AppointmentTypeRepository appointmentTypeRepository,
            PatientRepository patientRepository,
            BookingRepository bookingRepository,
            IndianMobileNumberValidator mobileNumberValidator,
            QueueSlotService queueSlotService,
            QueuePositionService queuePositionService,
            InboxItemService inboxItemService,
            SessionAvailabilityService sessionAvailabilityService) {
        this.sessionRepository = sessionRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.feeResolutionService = feeResolutionService;
        this.appointmentTypeRepository = appointmentTypeRepository;
        this.patientRepository = patientRepository;
        this.walkInPatientRegistrar = new WalkInPatientRegistrar(patientRepository);
        this.bookingRepository = bookingRepository;
        this.mobileNumberValidator = mobileNumberValidator;
        this.queueSlotService = queueSlotService;
        this.queuePositionService = queuePositionService;
        this.inboxItemService = inboxItemService;
        this.sessionAvailabilityService = sessionAvailabilityService;
    }

    @Transactional
    public Registration register(UUID callerAccountId, UUID clinicId, RegisterInput input) {
        Session session = sessionRepository
                .findById(input.sessionId())
                .filter(s -> s.getClinic().getId().equals(clinicId))
                .orElseThrow(() -> new SessionNotFoundException(input.sessionId()));

        requireAuthorized(callerAccountId, clinicId);
        // 062-rejected-clinic-gating (FR-001): before any validation that could write or lock.
        if (session.getClinic().isRejected()) {
            throw new ClinicNotAcceptingAppointmentsException(clinicId);
        }
        // 065-phase1-stabilization (research.md R4): a past or cancelled session - or one whose
        // cancelled range covers the current time - takes no walk-ins. A walk-in after the session's
        // scheduled end is still allowed (spec 063 "Session over").
        if (sessionAvailabilityService.evaluate(session, null) != Verdict.ACCEPTING) {
            throw new SessionNotAcceptingBookingsException(session.getId());
        }

        VisitReason visitReason = parseVisitReason(input.visitReason());
        String visitReasonDetail = validateDetail(visitReason, input.visitReasonDetail());

        Patient existingPatient = null;
        if (input.patientId() != null) {
            existingPatient = patientRepository
                    .findById(input.patientId())
                    .filter(p -> p.getClinic().getId().equals(clinicId))
                    .orElseThrow(() -> new PatientNotFoundException(input.patientId()));
            // FR-017: already waiting in, or booked into, this session - staff must confirm.
            if (!input.confirmDuplicate()
                    && bookingRepository.existsBySlot_Session_IdAndPatient_IdAndStatus(
                            session.getId(), existingPatient.getId(), BookingStatus.ACTIVE)) {
                throw new DuplicateWalkInException();
            }
        } else {
            validateNewPatient(input);
        }

        // The fee is the first real write-gate (015): nothing is written before it resolves.
        BigDecimal lockedFee = feeResolutionService.resolve(clinicId, session.getDoctorProfile().getId(), input.appointmentTypeId());
        AppointmentType appointmentType = appointmentTypeRepository
                .findById(input.appointmentTypeId())
                .orElseThrow(() -> new AppointmentTypeNotFoundException(input.appointmentTypeId()));

        Patient patient = existingPatient != null
                ? existingPatient
                // 074-duplicate-patient-phone: flushed before the token is issued, so a refusal leaves no token.
                : walkInPatientRegistrar.register(
                        session.getClinic(),
                        input.patientName().trim(),
                        blankToNull(input.patientPhone()),
                        blankToNull(input.patientEmail()));

        // Both issue a waiting (BOOKED) untimed token - 064-queue-send-in-complete minted every token
        // BOOKED at the single issuance point.
        Slot slot = session.getMode() == ScheduleMode.FIXED_TIME
                ? queueSlotService.issueNextWalkInSlot(session.getId())
                : queueSlotService.issueNextSlot(session.getId());

        Booking booking = bookingRepository.saveAndFlush(Booking.walkIn(
                slot, patient, appointmentType, lockedFee, callerAccountId, visitReason, visitReasonDetail));
        inboxItemService.createWalkInItem(session.getClinic(), booking);

        Integer walkInPosition = session.getMode() == ScheduleMode.FIXED_TIME
                ? queuePositionService.positionOf(booking).position()
                : null;
        return new Registration(booking, walkInPosition);
    }

    private VisitReason parseVisitReason(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new VisitReasonRequiredException();
        }
        try {
            return VisitReason.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new VisitReasonRequiredException();
        }
    }

    /** FR-004: detail is required for OTHER, optional otherwise, and never over 200 characters. */
    private String validateDetail(VisitReason reason, String rawDetail) {
        String detail = blankToNull(rawDetail);
        if (detail != null && detail.length() > VISIT_REASON_DETAIL_MAX) {
            throw new VisitReasonDetailRequiredException();
        }
        if (reason == VisitReason.OTHER && detail == null) {
            throw new VisitReasonDetailRequiredException();
        }
        return detail;
    }

    /** FR-002: a name is required; phone and email are optional but validated when given. */
    private void validateNewPatient(RegisterInput input) {
        if (input.patientName() == null || input.patientName().isBlank()) {
            throw new PatientRequiredException();
        }
        String phone = blankToNull(input.patientPhone());
        if (phone != null && !mobileNumberValidator.isValid(phone)) {
            throw new InvalidMobileNumberException();
        }
        String email = blankToNull(input.patientEmail());
        if (email != null && !EMAIL.matcher(email).matches()) {
            throw new InvalidEmailException();
        }
    }

    /** Operations or ClinicAdmin at this clinic - the same roles that registered walk-ins before (025). */
    private void requireAuthorized(UUID callerAccountId, UUID clinicId) {
        boolean isOperations = roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                callerAccountId, clinicId, RoleAssignment.Role.Operations);
        boolean isClinicAdmin = roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                callerAccountId, clinicId, RoleAssignment.Role.ClinicAdmin);
        if (!isOperations && !isClinicAdmin) {
            throw new ForbiddenException();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record RegisterInput(
            UUID sessionId,
            UUID patientId,
            String patientName,
            String patientPhone,
            String patientEmail,
            UUID appointmentTypeId,
            String visitReason,
            String visitReasonDetail,
            boolean confirmDuplicate) {}

    /** {@code walkInPosition} is the place in a Fixed-Time walk-in line; null for a Queue token (research.md Decision 7). */
    public record Registration(Booking booking, Integer walkInPosition) {}
}
