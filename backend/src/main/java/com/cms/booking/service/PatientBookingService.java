package com.cms.booking.service;

import com.cms.booking.exception.ClinicNotAcceptingAppointmentsException;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.Booking;
import com.cms.booking.exception.AppointmentTypeNotFoundException;
import com.cms.booking.exception.SessionNotAcceptingBookingsException;
import com.cms.booking.exception.SlotAlreadyBookedException;
import com.cms.booking.exception.SlotDateInThePastException;
import com.cms.booking.exception.SlotNotFoundException;
import com.cms.booking.repository.AppointmentTypeRepository;
import com.cms.booking.repository.BookingRepository;


import com.cms.booking.dto.AppointmentTypeResponse;
import com.cms.booking.dto.OpenSlotResponse;
import com.cms.booking.dto.PatientDoctorSummaryResponse;
import com.cms.booking.dto.QueueSessionResponse;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.patient.record.domain.Patient;
import com.cms.patient.record.service.PatientLinkingService;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.repository.SessionRepository;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.repository.SlotRepository;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.scheduling.service.SessionAvailabilityService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 021: the patient self-service analog of 020's {@link StaffBookingService} - same
 * fee-resolution-first-write-gate ordering and {@code saveAndFlush} race-closure pattern
 * (research.md), reused rather than re-derived. Differs from {@link StaffBookingService} in
 * exactly two ways: no authorization-role check (any authenticated Patient Account may book
 * for themselves), and patient resolution always goes through {@link PatientLinkingService}
 * rather than an existing-id-or-new-walk-in branch.
 */
@Service
public class PatientBookingService {

    private final SlotRepository slotRepository;
    private final SessionRepository sessionRepository;
    private final FeeResolutionService feeResolutionService;
    private final AppointmentTypeRepository appointmentTypeRepository;
    private final PatientLinkingService patientLinkingService;
    private final BookingRepository bookingRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final BookingProtectionService bookingProtectionService;
    private final ClinicRepository clinicRepository;
    private final SessionAvailabilityService sessionAvailabilityService;
    private final TransactionTemplate transactionTemplate;

    public PatientBookingService(
            SlotRepository slotRepository,
            SessionRepository sessionRepository,
            FeeResolutionService feeResolutionService,
            AppointmentTypeRepository appointmentTypeRepository,
            PatientLinkingService patientLinkingService,
            BookingRepository bookingRepository,
            DoctorProfileRepository doctorProfileRepository,
            BookingProtectionService bookingProtectionService,
            ClinicRepository clinicRepository,
            SessionAvailabilityService sessionAvailabilityService,
            PlatformTransactionManager transactionManager) {
        this.slotRepository = slotRepository;
        this.sessionRepository = sessionRepository;
        this.feeResolutionService = feeResolutionService;
        this.appointmentTypeRepository = appointmentTypeRepository;
        this.patientLinkingService = patientLinkingService;
        this.bookingRepository = bookingRepository;
        this.doctorProfileRepository = doctorProfileRepository;
        this.bookingProtectionService = bookingProtectionService;
        this.clinicRepository = clinicRepository;
        this.sessionAvailabilityService = sessionAvailabilityService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Doctors staffed at a clinic, patient-facing - backs the doctor picker replacing the raw
     * {@code doctorProfileId} text field on {@code JoinWaitlistForm}. Reuses {@link
     * DoctorProfileRepository#findByClinicStaffed} (the same query {@code ClinicDoctorController}
     * uses for staff), but with no active-role-at-clinic check on the caller - any authenticated
     * Patient Account may browse any clinic's public doctor roster, same access boundary as
     * {@link #listOpenSlots} and {@link #listQueueSessions} above.
     */
    @Transactional(readOnly = true)
    public Page<PatientDoctorSummaryResponse> listDoctors(UUID clinicId, String q, Pageable pageable) {
        String searchPattern = (q == null || q.isBlank()) ? null : "%" + q.trim().toLowerCase() + "%";
        return doctorProfileRepository
                .findByClinicStaffed(clinicId, searchPattern, pageable)
                .map(PatientDoctorSummaryResponse::from);
    }

    /**
     * A doctor's appointment types, patient-facing - backs the picker replacing {@code
     * ClaimOfferCard}'s raw "Appointment Type ID" text field. No ownership/role check on the
     * caller, same as {@link #listDoctors} - the exact query {@link #listOpenSlots}/{@link
     * #listQueueSessions} already run per-doctor to embed appointment types in their own
     * responses.
     */
    @Transactional(readOnly = true)
    public List<AppointmentTypeResponse> listAppointmentTypes(UUID doctorProfileId) {
        return appointmentTypeRepository.findByDoctorProfile_Id(doctorProfileId).stream()
                .map(AppointmentTypeResponse::of)
                .toList();
    }

    /**
     * FR-011/FR-012: only currently-OPEN Fixed-Time Slots; no resolved fee amounts (research.md).
     * patient-slot-booking-date-logic: {@code date}, when given, narrows to exactly that day (the
     * date-strip picker) - the present-day-or-later floor is enforced unconditionally in both
     * repository query variants regardless of whether {@code date} is set. The null/non-null
     * branch here (rather than one query with a nullable {@code date} param) is deliberate - see
     * {@code SlotRepository.findOpenFixedTimeSlots}'s own javadoc for why.
     *
     * <p>065-phase1-stabilization: both queries also drop today's elapsed slots and slots inside a
     * cancelled session or range, using {@link SessionAvailabilityService}'s clock - the listing never
     * offers what {@link #bookSlot} would refuse.
     */
    @Transactional(readOnly = true)
    public Page<OpenSlotResponse> listOpenSlots(UUID clinicId, UUID doctorProfileId, LocalDate date, Pageable pageable) {
        LocalDateTime now = sessionAvailabilityService.now();
        LocalDate today = now.toLocalDate();
        Page<Slot> slotPage = date != null
                ? slotRepository.findOpenFixedTimeSlotsOnDate(
                        clinicId, doctorProfileId, date, today, now.toLocalTime(), pageable)
                : slotRepository.findOpenFixedTimeSlots(clinicId, doctorProfileId, today, now.toLocalTime(), pageable);
        Map<UUID, List<AppointmentTypeResponse>> appointmentTypesByDoctor = new HashMap<>();
        return slotPage.map(slot -> {
            UUID doctorId = slot.getSession().getDoctorProfile().getId();
            List<AppointmentTypeResponse> appointmentTypes = appointmentTypesByDoctor.computeIfAbsent(
                    doctorId,
                    id -> appointmentTypeRepository.findByDoctorProfile_Id(id).stream()
                            .map(AppointmentTypeResponse::of)
                            .toList());
            String doctorName = slot.getSession().getDoctorProfile().getAccount().getName();
            return OpenSlotResponse.of(slot, doctorName, appointmentTypes);
        });
    }

    /**
     * patient-booking-flow-rebuild: every today-or-later Queue-mode Session at a clinic - the
     * browse list replacing the raw "type a Session ID" field, mirroring {@link #listOpenSlots}'s
     * own doctor-name/appointment-type resolution and per-doctor caching.
     *
     * <p>065-phase1-stabilization: excludes whole-cancelled sessions, and today's sessions whose
     * cancelled range covers the current time.
     */
    @Transactional(readOnly = true)
    public Page<QueueSessionResponse> listQueueSessions(UUID clinicId, UUID doctorProfileId, Pageable pageable) {
        LocalDateTime now = sessionAvailabilityService.now();
        Page<Session> sessionPage = sessionRepository.findUpcomingQueueSessionsByClinic(
                clinicId, doctorProfileId, now.toLocalDate(), now.toLocalTime(), pageable);
        Map<UUID, List<AppointmentTypeResponse>> appointmentTypesByDoctor = new HashMap<>();
        return sessionPage.map(session -> {
            UUID doctorId = session.getDoctorProfile().getId();
            List<AppointmentTypeResponse> appointmentTypes = appointmentTypesByDoctor.computeIfAbsent(
                    doctorId,
                    id -> appointmentTypeRepository.findByDoctorProfile_Id(id).stream()
                            .map(AppointmentTypeResponse::of)
                            .toList());
            String doctorName = session.getDoctorProfile().getAccount().getName();
            return QueueSessionResponse.of(session, doctorName, appointmentTypes);
        });
    }

    public Booking bookSlot(UUID patientAccountId, UUID clinicId, UUID slotId, BookSlotInput input) {
        // 062-rejected-clinic-gating (research.md Decision 1, "Ordering"): refused before the 060
        // rate-limit gate below, so an attempt at a rejected clinic is never recorded as booking
        // activity (which would also block that clinic's guarded permanent-delete).
        requireClinicAcceptingAppointments(clinicId);
        // 060-booking-abuse-prevention: the rate-limit/booking-limit gate - the very first thing
        // this method does, before the slot lookup, matching research.md Decision 6's fixed check
        // order. The gate, its attempt row and the booking share one transaction, so the patient
        // row lock the gate takes is held until the booking commits (research.md Decision 1).
        try {
            return transactionTemplate.execute(status -> {
                UUID attemptId = bookingProtectionService.checkAndRecordAttempt(patientAccountId, clinicId);
                Booking booking = doBookSlot(patientAccountId, clinicId, slotId, input);
                bookingProtectionService.recordSuccess(attemptId, booking);
                return booking;
            });
        } catch (RuntimeException e) {
            // FR-008: every attempt counts, whatever the reason - a rejection by the gate, or a
            // failure below it (slot already taken, slot in the past, no fee configured, etc.).
            // Its row rolled back with the transaction; recorded now that the lock is released.
            bookingProtectionService.recordAfterRollback(patientAccountId, clinicId, e);
            throw e;
        }
    }

    private Booking doBookSlot(UUID patientAccountId, UUID clinicId, UUID slotId, BookSlotInput input) {
        Slot slot = slotRepository
                .findById(slotId)
                .filter(s -> s.getSession().getClinic().getId().equals(clinicId))
                // 063-front-desk-walk-in: an untimed slot is a place in a walk-in line or a queue
                // token, never a bookable appointment time - booked only by the front-desk flow.
                .filter(s -> !s.isUntimed())
                .orElseThrow(() -> new SlotNotFoundException(slotId));

        if (slot.getStatus() != SlotStatus.OPEN) {
            throw new SlotAlreadyBookedException(slotId);
        }

        // 065-phase1-stabilization (research.md R4): the shared bookability rule, subsuming the
        // earlier date-only check - defense in depth for a stale client, since listOpenSlots
        // applies the same rule. Also covers the waitlist claim, which books through here.
        switch (sessionAvailabilityService.evaluate(slot.getSession(), slot)) {
            case PAST_DATE, ELAPSED -> throw new SlotDateInThePastException(slotId);
            case CANCELLED -> throw new SessionNotAcceptingBookingsException(slot.getSession().getId());
            case ACCEPTING -> {}
        }

        UUID doctorProfileId = slot.getSession().getDoctorProfile().getId();

        // FR-003: the first real write-gate - nothing is written before this succeeds.
        BigDecimal lockedFee = feeResolutionService.resolve(doctorProfileId, input.appointmentTypeId());
        // Already proven to exist and belong to this doctor by the successful resolve() call above.
        AppointmentType appointmentType = appointmentTypeRepository
                .findById(input.appointmentTypeId())
                .orElseThrow(() -> new AppointmentTypeNotFoundException(input.appointmentTypeId()));

        // FR-004/FR-005: resolves the existing link, phone-matches an unlinked walk-in
        // record, or creates a new one - uniformly, for every booking (019's own contract).
        Patient patient = patientLinkingService.findOrCreatePatient(patientAccountId, clinicId, input.patientName());

        // _diagnostics CRITICAL fix: patientAccountId is a patient_account.id, never an
        // account.id - Booking.bookedByPatient sets the correct disjoint-identity column.
        Booking booking = Booking.bookedByPatient(slot, patient, appointmentType, lockedFee, patientAccountId);
        try {
            // saveAndFlush (not save) forces the INSERT - and its uq_booking_slot
            // constraint check - to happen synchronously right here, where this catch
            // block can actually intercept it (020's convergence-fixed race closure).
            booking = bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException e) {
            throw new SlotAlreadyBookedException(slotId);
        }

        slot.setStatus(SlotStatus.BOOKED);
        return booking;
    }

    /** 062-rejected-clinic-gating (FR-001): a rejected clinic takes no appointments until restored. */
    private void requireClinicAcceptingAppointments(UUID clinicId) {
        if (clinicRepository.findById(clinicId).map(Clinic::isRejected).orElse(false)) {
            throw new ClinicNotAcceptingAppointmentsException(clinicId);
        }
    }

    public record BookSlotInput(String patientName, UUID appointmentTypeId) {}
}
