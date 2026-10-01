package com.cms.booking.service;

import com.cms.booking.exception.ClinicNotAcceptingAppointmentsException;
import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.Booking;
import com.cms.booking.exception.AppointmentTypeNotFoundException;
import com.cms.booking.exception.ForbiddenException;
import com.cms.booking.exception.InvalidMobileNumberException;
import com.cms.booking.exception.PatientNotFoundException;
import com.cms.booking.exception.SessionNotAcceptingBookingsException;
import com.cms.booking.exception.SlotAlreadyBookedException;
import com.cms.booking.exception.SlotDateInThePastException;
import com.cms.booking.exception.SlotNotFoundException;
import com.cms.booking.repository.AppointmentTypeRepository;
import com.cms.booking.repository.BookingRepository;


import com.cms.common.IndianMobileNumberValidator;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.patient.record.domain.Patient;
import com.cms.patient.record.repository.PatientRepository;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.repository.SlotRepository;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.scheduling.service.SessionAvailabilityService;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 020: authorize -> validate Slot is OPEN -> resolve-and-lock the fee (015, the first
 * real write-gate - nothing is written before this succeeds) -> resolve-or-create the
 * Patient (009) -> save the Booking -> flip the Slot to BOOKED. See data-model.md.
 */
@Service
public class StaffBookingService {

    private final SlotRepository slotRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final FeeResolutionService feeResolutionService;
    private final AppointmentTypeRepository appointmentTypeRepository;
    private final PatientRepository patientRepository;
    private final BookingRepository bookingRepository;
    private final IndianMobileNumberValidator mobileNumberValidator;
    private final SessionAvailabilityService sessionAvailabilityService;

    public StaffBookingService(
            SlotRepository slotRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            FeeResolutionService feeResolutionService,
            AppointmentTypeRepository appointmentTypeRepository,
            PatientRepository patientRepository,
            BookingRepository bookingRepository,
            IndianMobileNumberValidator mobileNumberValidator,
            SessionAvailabilityService sessionAvailabilityService) {
        this.sessionAvailabilityService = sessionAvailabilityService;
        this.slotRepository = slotRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.feeResolutionService = feeResolutionService;
        this.appointmentTypeRepository = appointmentTypeRepository;
        this.patientRepository = patientRepository;
        this.bookingRepository = bookingRepository;
        this.mobileNumberValidator = mobileNumberValidator;
    }

    @Transactional
    public Booking bookSlot(UUID callerAccountId, UUID clinicId, UUID slotId, BookSlotInput input) {
        Slot slot = slotRepository
                .findById(slotId)
                .filter(s -> s.getSession().getClinic().getId().equals(clinicId))
                // 063-front-desk-walk-in: an untimed slot is a place in a walk-in line or a queue
                // token, never a bookable appointment time - booked only by the front-desk flow.
                .filter(s -> !s.isUntimed())
                .orElseThrow(() -> new SlotNotFoundException(slotId));

        requireAuthorized(callerAccountId, clinicId);
        // 062-rejected-clinic-gating (FR-001): after the existing role check, before any write.
        if (slot.getSession().getClinic().isRejected()) {
            throw new ClinicNotAcceptingAppointmentsException(clinicId);
        }

        if (slot.getStatus() != SlotStatus.OPEN) {
            throw new SlotAlreadyBookedException(slotId);
        }

        // 065-phase1-stabilization (research.md R4): the shared bookability rule - staff booking had
        // no date or time check at all before this.
        switch (sessionAvailabilityService.evaluate(slot.getSession(), slot)) {
            case PAST_DATE, ELAPSED -> throw new SlotDateInThePastException(slotId);
            case CANCELLED -> throw new SessionNotAcceptingBookingsException(slot.getSession().getId());
            case ACCEPTING -> {}
        }

        UUID doctorProfileId = slot.getSession().getDoctorProfile().getId();

        // FR-004: the first real write-gate - nothing is written before this succeeds.
        BigDecimal lockedFee = feeResolutionService.resolve(clinicId, doctorProfileId, input.appointmentTypeId());
        // Already proven to exist and belong to this doctor by the successful resolve() call above.
        AppointmentType appointmentType = appointmentTypeRepository
                .findById(input.appointmentTypeId())
                .orElseThrow(() -> new AppointmentTypeNotFoundException(input.appointmentTypeId()));

        Patient patient = resolveOrCreatePatient(clinicId, slot, input);

        Booking booking = new Booking(slot, patient, appointmentType, lockedFee, callerAccountId);
        try {
            // Convergence fix: saveAndFlush (not save) forces the INSERT - and its
            // uq_booking_slot constraint check - to happen synchronously right here,
            // where this catch block can actually intercept it. A plain save() defers
            // the INSERT to the transaction's eventual commit, by which point this
            // method has already returned and no catch here could ever fire.
            booking = bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException e) {
            // Lost a concurrent race for this Slot - the DB unique constraint is the real guarantee (research.md).
            throw new SlotAlreadyBookedException(slotId);
        }

        slot.setStatus(SlotStatus.BOOKED);
        return booking;
    }

    private Patient resolveOrCreatePatient(UUID clinicId, Slot slot, BookSlotInput input) {
        if (input.patientId() != null) {
            return patientRepository
                    .findById(input.patientId())
                    .filter(p -> p.getClinic().getId().equals(clinicId))
                    .orElseThrow(() -> new PatientNotFoundException(input.patientId()));
        }

        if (!mobileNumberValidator.isValid(input.patientPhone())) {
            throw new InvalidMobileNumberException();
        }
        return patientRepository.save(
                new Patient(slot.getSession().getClinic(), null, input.patientName(), input.patientPhone()));
    }

    /** FR-001/FR-002: an active Operations or ClinicAdmin at this clinic - never the Doctor (spec Scope Decisions). */
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
