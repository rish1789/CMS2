package com.cms.clinical;

import com.cms.booking.Booking;
import com.cms.identity.doctor.DoctorProfile;
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

    public ConsultationNoteService(
            TreatingDoctorAuthorizationService treatingDoctorAuthorizationService,
            ConsultationNoteRepository consultationNoteRepository) {
        this.treatingDoctorAuthorizationService = treatingDoctorAuthorizationService;
        this.consultationNoteRepository = consultationNoteRepository;
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
        DoctorProfile treatingDoctor = treatingDoctorAuthorizationService.requireTreatingDoctor(booking, callerAccountId);

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
}
