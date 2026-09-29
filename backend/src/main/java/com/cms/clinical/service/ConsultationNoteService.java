package com.cms.clinical.service;

import com.cms.clinical.domain.ConsultationNote;
import com.cms.clinical.exception.ConsultationNoteAlreadyExistsException;
import com.cms.clinical.exception.ConsultationNoteNotFoundException;
import com.cms.clinical.repository.ConsultationNoteRepository;


import com.cms.booking.domain.Booking;
import com.cms.booking.exception.BookingNotFoundException;
import com.cms.booking.repository.BookingRepository;
import com.cms.identity.doctor.DoctorProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 034: no update/delete method exists anywhere in this class (research.md R7).
 *
 * <p>035 research.md R2: the booking-lookup and treating-doctor-identity-trace logic this class
 * used to own privately is now shared, via {@link TreatingDoctorAuthorizationService}, with
 * {@code PrescriptionService} - a behavior-preserving extraction, not a change to this class's
 * own contract.
 */
@Service
public class ConsultationNoteService {

    private final TreatingDoctorAuthorizationService treatingDoctorAuthorizationService;
    private final ConsultationNoteRepository consultationNoteRepository;
    private final BookingRepository bookingRepository;

    public ConsultationNoteService(
            TreatingDoctorAuthorizationService treatingDoctorAuthorizationService,
            ConsultationNoteRepository consultationNoteRepository,
            BookingRepository bookingRepository) {
        this.treatingDoctorAuthorizationService = treatingDoctorAuthorizationService;
        this.consultationNoteRepository = consultationNoteRepository;
        this.bookingRepository = bookingRepository;
    }

    /**
     * FR-001/FR-003/FR-005 (research.md R2): the actual one-note-per-booking guarantee is the
     * table's own {@code UNIQUE} constraint on {@code booking_id} - {@code saveAndFlush} forces
     * the INSERT (and that constraint check) to happen synchronously right here, where this
     * catch block can actually intercept it, mirroring 021's identical race-closure shape.
     */
    @Transactional
    public ConsultationNote create(UUID clinicId, UUID bookingId, UUID callerAccountId, String content) {
        Booking booking = treatingDoctorAuthorizationService.findBookingInClinic(clinicId, bookingId);
        DoctorProfile treatingDoctor = treatingDoctorAuthorizationService.requireActiveTreatingDoctor(booking, callerAccountId);

        try {
            return consultationNoteRepository.saveAndFlush(new ConsultationNote(booking, treatingDoctor, content));
        } catch (DataIntegrityViolationException e) {
            throw new ConsultationNoteAlreadyExistsException(bookingId);
        }
    }

    /** FR-006: the treating doctor retrieves their own note - no broader read surface (research.md R5). */
    @Transactional(readOnly = true)
    public ConsultationNote get(UUID clinicId, UUID bookingId, UUID callerAccountId) {
        Booking booking = treatingDoctorAuthorizationService.findBookingInClinic(clinicId, bookingId);
        treatingDoctorAuthorizationService.requireTreatingDoctor(booking, callerAccountId);

        return consultationNoteRepository
                .findByBooking_Id(bookingId)
                .orElseThrow(() -> new ConsultationNoteNotFoundException(bookingId));
    }

    /**
     * 059-patient-clinical-record-access FR-001 (research.md Decision 1/2): a patient reads their
     * own note, if any - the exact same {@code findByBooking_Id} lookup {@link #get} above already
     * uses, so a purged note is simply absent here too, with no separate check. An empty result is
     * a valid, successful "nothing written yet" state, not an error (research.md Decision 5).
     */
    @Transactional(readOnly = true)
    public Optional<ConsultationNote> getForPatient(UUID bookingId, UUID patientAccountId) {
        bookingRepository
                .findByIdAndPatient_PatientAccount_Id(bookingId, patientAccountId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        return consultationNoteRepository.findByBooking_Id(bookingId);
    }
}
