package com.cms.booking;

import com.cms.patient.record.Patient;
import com.cms.patient.record.PatientLinkingService;
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

    public PatientQueueBookingService(
            SessionRepository sessionRepository,
            QueueSlotService queueSlotService,
            FeeResolutionService feeResolutionService,
            AppointmentTypeRepository appointmentTypeRepository,
            PatientLinkingService patientLinkingService,
            BookingRepository bookingRepository) {
        this.sessionRepository = sessionRepository;
        this.queueSlotService = queueSlotService;
        this.feeResolutionService = feeResolutionService;
        this.appointmentTypeRepository = appointmentTypeRepository;
        this.patientLinkingService = patientLinkingService;
        this.bookingRepository = bookingRepository;
    }

    public Booking bookSlot(UUID patientAccountId, UUID clinicId, UUID sessionId, BookSlotInput input) {
        Session session = sessionRepository
                .findById(sessionId)
                .filter(s -> s.getClinic().getId().equals(clinicId))
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (session.getMode() != ScheduleMode.QUEUE) {
            throw new NotAQueueSessionException(sessionId);
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

    public record BookSlotInput(String patientName, UUID appointmentTypeId) {}
}
