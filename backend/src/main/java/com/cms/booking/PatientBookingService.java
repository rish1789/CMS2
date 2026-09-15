package com.cms.booking;

import com.cms.booking.dto.AppointmentTypeResponse;
import com.cms.booking.dto.OpenSlotResponse;
import com.cms.booking.dto.PatientDoctorSummaryResponse;
import com.cms.booking.dto.QueueSessionResponse;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.patient.record.Patient;
import com.cms.patient.record.PatientLinkingService;
import com.cms.scheduling.Session;
import com.cms.scheduling.SessionRepository;
import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotRepository;
import com.cms.scheduling.SlotStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public PatientBookingService(
            SlotRepository slotRepository,
            SessionRepository sessionRepository,
            FeeResolutionService feeResolutionService,
            AppointmentTypeRepository appointmentTypeRepository,
            PatientLinkingService patientLinkingService,
            BookingRepository bookingRepository,
            DoctorProfileRepository doctorProfileRepository) {
        this.slotRepository = slotRepository;
        this.sessionRepository = sessionRepository;
        this.feeResolutionService = feeResolutionService;
        this.appointmentTypeRepository = appointmentTypeRepository;
        this.patientLinkingService = patientLinkingService;
        this.bookingRepository = bookingRepository;
        this.doctorProfileRepository = doctorProfileRepository;
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
     */
    @Transactional(readOnly = true)
    public Page<OpenSlotResponse> listOpenSlots(UUID clinicId, UUID doctorProfileId, LocalDate date, Pageable pageable) {
        LocalDate today = LocalDate.now();
        Page<Slot> slotPage = date != null
                ? slotRepository.findOpenFixedTimeSlotsOnDate(clinicId, doctorProfileId, date, today, pageable)
                : slotRepository.findOpenFixedTimeSlots(clinicId, doctorProfileId, today, pageable);
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
     */
    @Transactional(readOnly = true)
    public Page<QueueSessionResponse> listQueueSessions(UUID clinicId, UUID doctorProfileId, Pageable pageable) {
        Page<Session> sessionPage =
                sessionRepository.findUpcomingQueueSessionsByClinic(clinicId, doctorProfileId, LocalDate.now(), pageable);
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

    @Transactional
    public Booking bookSlot(UUID patientAccountId, UUID clinicId, UUID slotId, BookSlotInput input) {
        Slot slot = slotRepository
                .findById(slotId)
                .filter(s -> s.getSession().getClinic().getId().equals(clinicId))
                .orElseThrow(() -> new SlotNotFoundException(slotId));

        if (slot.getStatus() != SlotStatus.OPEN) {
            throw new SlotAlreadyBookedException(slotId);
        }

        // patient-slot-booking-date-logic: defense in depth - listOpenSlots already floors what
        // it offers to today-or-later, but this closes the gap for a stale client.
        if (slot.getSession().getSessionDate().isBefore(LocalDate.now())) {
            throw new SlotDateInThePastException(slotId);
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

    public record BookSlotInput(String patientName, UUID appointmentTypeId) {}
}
