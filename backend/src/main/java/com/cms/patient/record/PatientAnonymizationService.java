package com.cms.patient.record;

import com.cms.booking.BookingRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 037: the sole service for this feature. Reads {@link BookingRepository} directly for its own
 * synchronous precondition check (research.md R1) - a read-only reach into a module that already
 * depends on this one, not the reverse (research.md R3).
 */
@Service
public class PatientAnonymizationService {

    private final PatientRepository patientRepository;
    private final BookingRepository bookingRepository;

    public PatientAnonymizationService(PatientRepository patientRepository, BookingRepository bookingRepository) {
        this.patientRepository = patientRepository;
        this.bookingRepository = bookingRepository;
    }

    /**
     * FR-001..FR-004/FR-007 (research.md R4/R5): already-anonymized is checked first and treated
     * as a pure no-op, skipping the booking check entirely - a later booking existing for an
     * already-anonymized patient is not this action's concern to re-validate.
     */
    @Transactional
    public Patient anonymize(UUID clinicId, UUID patientId) {
        Patient patient = patientRepository
                .findById(patientId)
                .filter(p -> p.getClinic().getId().equals(clinicId))
                .orElseThrow(() -> new PatientNotFoundException(patientId));

        if (patient.isAnonymized()) {
            return patient;
        }

        if (bookingRepository.existsActiveFutureBookingForPatient(patientId)) {
            throw new PatientHasActiveFutureBookingException(patientId);
        }

        patient.anonymize();
        return patientRepository.save(patient);
    }
}
