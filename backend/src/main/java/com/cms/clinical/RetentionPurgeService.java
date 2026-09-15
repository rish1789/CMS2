package com.cms.clinical;

import com.cms.booking.Booking;
import com.cms.booking.BookingRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 038: the DPDP monthly-purge half of the anonymization/retention lifecycle (constitution
 * Principle IV). Permanently deletes a booking's Consultation Note, Prescriptions/Items, and
 * External Record References once BOTH hold (research.md R1/R4): the booking's own {@code
 * createdAt} is 3+ years old, AND its patient is already anonymized (037). Never deletes the
 * Booking or Patient row itself. Reads {@code com.cms.booking}/{@code com.cms.patient.record}
 * directly for its own precondition checks, consistent with this module's existing services
 * (research.md R6).
 */
@Service
public class RetentionPurgeService {

    private static final Period RETENTION_PERIOD = Period.ofYears(3);

    private final BookingRepository bookingRepository;
    private final ConsultationNoteRepository consultationNoteRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final ExternalRecordReferenceRepository externalRecordReferenceRepository;
    private final Clock clock;

    // Explicit @Autowired: this class has a second (package-private, test-only) constructor
    // below, and Spring's implicit single-constructor autowiring only applies when there is
    // exactly one constructor - with two present and neither previously annotated, Spring
    // instead tried a no-arg constructor and failed. Never caught by this session's own tests,
    // since every @SpringBootTest here is Testcontainers-gated and fails before Spring ever
    // attempts to start the context. Found only by actually booting the full app (038's own
    // quickstart run).
    @Autowired
    public RetentionPurgeService(
            BookingRepository bookingRepository,
            ConsultationNoteRepository consultationNoteRepository,
            PrescriptionRepository prescriptionRepository,
            ExternalRecordReferenceRepository externalRecordReferenceRepository) {
        this(
                bookingRepository,
                consultationNoteRepository,
                prescriptionRepository,
                externalRecordReferenceRepository,
                Clock.systemUTC());
    }

    RetentionPurgeService(
            BookingRepository bookingRepository,
            ConsultationNoteRepository consultationNoteRepository,
            PrescriptionRepository prescriptionRepository,
            ExternalRecordReferenceRepository externalRecordReferenceRepository,
            Clock clock) {
        this.bookingRepository = bookingRepository;
        this.consultationNoteRepository = consultationNoteRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.externalRecordReferenceRepository = externalRecordReferenceRepository;
        this.clock = clock;
    }

    /**
     * FR-001/FR-002/FR-003/FR-011: runs the full sweep and returns the number of bookings whose
     * clinical content was purged. Idempotent - a booking with nothing left to delete is a safe
     * no-op on a re-run, since eligibility is re-derived from {@link BookingRepository} each time
     * (research.md's "no state machine" data-model note).
     */
    @Transactional
    public int purge() {
        Instant retentionCutoff =
                LocalDate.now(clock).minus(RETENTION_PERIOD).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<Booking> eligibleBookings = bookingRepository.findRetentionEligibleBookings(retentionCutoff);

        for (Booking booking : eligibleBookings) {
            purgeContentFor(booking.getId());
        }

        return eligibleBookings.size();
    }

    private void purgeContentFor(UUID bookingId) {
        consultationNoteRepository.findByBooking_Id(bookingId).ifPresent(consultationNoteRepository::delete);

        for (Prescription prescription : prescriptionRepository.findByBooking_Id(bookingId)) {
            prescriptionRepository.delete(prescription);
        }

        for (ExternalRecordReference reference : externalRecordReferenceRepository.findByBooking_Id(bookingId)) {
            externalRecordReferenceRepository.delete(reference);
        }
    }
}
