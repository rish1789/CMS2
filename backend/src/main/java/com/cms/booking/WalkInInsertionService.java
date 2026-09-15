package com.cms.booking;

import com.cms.common.IndianMobileNumberValidator;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.inbox.InboxItemService;
import com.cms.patient.record.Patient;
import com.cms.patient.record.PatientRepository;
import com.cms.scheduling.NotAFixedTimeSessionException;
import com.cms.scheduling.ScheduleMode;
import com.cms.scheduling.Session;
import com.cms.scheduling.SessionDelayService;
import com.cms.scheduling.SessionNotFoundException;
import com.cms.scheduling.SessionRepository;
import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotRepository;
import com.cms.scheduling.SlotStatus;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 025: for a Session, search - in strict priority order - (1) an OPEN buffer Slot, (2) a
 * Slot marked NO_SHOW, (3) any other OPEN regular Slot (override reason required) - then
 * resolve-and-lock the fee (015, the first real write-gate), resolve-or-create the walk-in
 * Patient (016's pattern, duplicated per research.md R4), and save the Booking. See
 * data-model.md and research.md R1-R3 for the tier search and write ordering.
 */
@Service
public class WalkInInsertionService {

    private final SessionRepository sessionRepository;
    private final SlotRepository slotRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final FeeResolutionService feeResolutionService;
    private final AppointmentTypeRepository appointmentTypeRepository;
    private final PatientRepository patientRepository;
    private final BookingRepository bookingRepository;
    private final IndianMobileNumberValidator mobileNumberValidator;
    private final SessionDelayService sessionDelayService;
    private final InboxItemService inboxItemService;

    public WalkInInsertionService(
            SessionRepository sessionRepository,
            SlotRepository slotRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            FeeResolutionService feeResolutionService,
            AppointmentTypeRepository appointmentTypeRepository,
            PatientRepository patientRepository,
            BookingRepository bookingRepository,
            IndianMobileNumberValidator mobileNumberValidator,
            SessionDelayService sessionDelayService,
            InboxItemService inboxItemService) {
        this.sessionRepository = sessionRepository;
        this.slotRepository = slotRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.feeResolutionService = feeResolutionService;
        this.appointmentTypeRepository = appointmentTypeRepository;
        this.patientRepository = patientRepository;
        this.bookingRepository = bookingRepository;
        this.mobileNumberValidator = mobileNumberValidator;
        this.sessionDelayService = sessionDelayService;
        this.inboxItemService = inboxItemService;
    }

    @Transactional
    public Booking insertWalkIn(UUID callerAccountId, UUID clinicId, UUID sessionId, WalkInInsertionInput input) {
        Session session = sessionRepository
                .findById(sessionId)
                .filter(s -> s.getClinic().getId().equals(clinicId))
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        requireAuthorized(callerAccountId, clinicId);

        // spec Assumptions/Edge Cases: Fixed-Time-only - buffer/no-show-freed Slots have no
        // Queue-mode equivalent; Queue-mode walk-ins are served by 018's own on-demand
        // issuance instead (mirrors StaffQueueBookingService's inverse NotAQueueSessionException,
        // and its identical load -> authorize -> mode-check ordering).
        if (session.getMode() != ScheduleMode.FIXED_TIME) {
            throw new NotAFixedTimeSessionException(sessionId);
        }

        Tier tier = selectTier(sessionId);

        String overrideReason = input.overrideReason();
        if (tier.requiresOverrideReason() && (overrideReason == null || overrideReason.isBlank())) {
            throw new OverrideReasonRequiredException();
        }
        String reasonToStore = tier.requiresOverrideReason() ? overrideReason : null;

        UUID doctorProfileId = session.getDoctorProfile().getId();

        // FR-005: the first real write-gate - nothing is written before this succeeds.
        BigDecimal lockedFee = feeResolutionService.resolve(doctorProfileId, input.appointmentTypeId());
        AppointmentType appointmentType = appointmentTypeRepository
                .findById(input.appointmentTypeId())
                .orElseThrow(() -> new AppointmentTypeNotFoundException(input.appointmentTypeId()));

        Patient patient = resolveOrCreatePatient(clinicId, session, input);

        if (tier.isNoShowReplacement()) {
            // FR-001a: only removed once fee/patient resolution have already succeeded above.
            // Explicitly flushed here (not left to the later saveAndFlush below) because
            // Hibernate's default flush ordering runs pending inserts before pending
            // deletes - without this, the new Booking's INSERT would run first and collide
            // with uq_booking_slot against the still-present old row (research.md R2/R3).
            // 028: findBySlot_IdAndStatus (not the bare findBySlot_Id) - a Slot can now carry
            // more than one historical Booking row after repeated book/cancel/rebook cycles;
            // this scopes to the specific live Booking a no-show Slot always has exactly one of.
            bookingRepository.findBySlot_IdAndStatus(tier.slot().getId(), BookingStatus.ACTIVE).ifPresent(old -> {
                bookingRepository.delete(old);
                bookingRepository.flush();
            });
        }

        Booking booking = new Booking(tier.slot(), patient, appointmentType, lockedFee, callerAccountId, reasonToStore);
        try {
            // Mirrors 016's convergence-fixed pattern: saveAndFlush forces the INSERT - and
            // its uq_booking_slot constraint check - to happen synchronously here, where this
            // catch block can actually intercept a lost concurrent race.
            booking = bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException e) {
            throw new SlotAlreadyBookedException(tier.slot().getId());
        }

        tier.slot().setStatus(SlotStatus.BOOKED);

        // 026-session-delay-tracking FR-003: the second of exactly two recalculation trigger
        // points, called as part of this same action/transaction (research.md R5).
        sessionDelayService.recalculate(sessionId);

        // 038-unified-realtime-inbox FR-002: a direct call, not an event - both modules already
        // exist in the same build (research.md R7).
        inboxItemService.createWalkInItem(session.getClinic(), booking);

        return booking;
    }

    /** FR-001: strict priority order, tier-internal ties broken by earliest startTime (analyze finding E2). */
    private Tier selectTier(UUID sessionId) {
        List<Slot> slots = slotRepository.findBySession_Id(sessionId).stream()
                .sorted(Comparator.comparing(Slot::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        Optional<Slot> bufferSlot =
                slots.stream().filter(Slot::isBuffer).filter(s -> s.getStatus() == SlotStatus.OPEN).findFirst();
        if (bufferSlot.isPresent()) {
            return new Tier(bufferSlot.get(), false, false);
        }

        Optional<Slot> noShowSlot =
                slots.stream().filter(s -> s.getStatus() == SlotStatus.NO_SHOW).findFirst();
        if (noShowSlot.isPresent()) {
            return new Tier(noShowSlot.get(), false, true);
        }

        Optional<Slot> regularSlot = slots.stream()
                .filter(s -> !s.isBuffer())
                .filter(s -> s.getStatus() == SlotStatus.OPEN)
                .findFirst();
        if (regularSlot.isPresent()) {
            return new Tier(regularSlot.get(), true, false);
        }

        throw new NoSlotAvailableException(sessionId);
    }

    private Patient resolveOrCreatePatient(UUID clinicId, Session session, WalkInInsertionInput input) {
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

    /** Mirrors 020/022's requireAuthorized: an active Operations or ClinicAdmin at this clinic - never the Doctor. */
    private void requireAuthorized(UUID callerAccountId, UUID clinicId) {
        boolean isOperations = roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                callerAccountId, clinicId, RoleAssignment.Role.Operations);
        boolean isClinicAdmin = roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                callerAccountId, clinicId, RoleAssignment.Role.ClinicAdmin);

        if (!isOperations && !isClinicAdmin) {
            throw new ForbiddenException();
        }
    }

    public record WalkInInsertionInput(
            UUID patientId, String patientName, String patientPhone, UUID appointmentTypeId, String overrideReason) {}

    private record Tier(Slot slot, boolean requiresOverrideReason, boolean isNoShowReplacement) {}
}
