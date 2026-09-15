package com.cms.booking;

import com.cms.common.IndianMobileNumberValidator;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.patient.record.Patient;
import com.cms.patient.record.PatientRepository;
import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotRepository;
import com.cms.scheduling.SlotStatus;
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

    public StaffBookingService(
            SlotRepository slotRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            FeeResolutionService feeResolutionService,
            AppointmentTypeRepository appointmentTypeRepository,
            PatientRepository patientRepository,
            BookingRepository bookingRepository,
            IndianMobileNumberValidator mobileNumberValidator) {
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
                .orElseThrow(() -> new SlotNotFoundException(slotId));

        requireAuthorized(callerAccountId, clinicId);

        if (slot.getStatus() != SlotStatus.OPEN) {
            throw new SlotAlreadyBookedException(slotId);
        }

        UUID doctorProfileId = slot.getSession().getDoctorProfile().getId();

        // FR-004: the first real write-gate - nothing is written before this succeeds.
        BigDecimal lockedFee = feeResolutionService.resolve(doctorProfileId, input.appointmentTypeId());
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
