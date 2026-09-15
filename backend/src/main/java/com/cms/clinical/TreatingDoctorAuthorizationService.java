package com.cms.clinical;

import com.cms.booking.Booking;
import com.cms.booking.BookingNotFoundException;
import com.cms.booking.BookingRepository;
import com.cms.identity.doctor.DoctorProfile;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 035 research.md R2: extracted from {@link ConsultationNoteService} (030) now that a second
 * real caller ({@link PrescriptionService}) needs the identical logic - a booking lookup scoped
 * to its clinic, then an identity trace (Booking -> Slot -> Session -> DoctorProfile) with no
 * clinic-ownership override of any kind. Shared by every clinical documentation type in this
 * module that restricts authorship to the treating doctor.
 */
@Service
public class TreatingDoctorAuthorizationService {

    private final BookingRepository bookingRepository;

    public TreatingDoctorAuthorizationService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Transactional(readOnly = true)
    public Booking findBookingInClinic(UUID clinicId, UUID bookingId) {
        return bookingRepository
                .findById(bookingId)
                .filter(b -> b.getSlot().getSession().getClinic().getId().equals(clinicId))
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }

    /** No ClinicAdmin or peer-doctor override of any kind. */
    public DoctorProfile requireTreatingDoctor(Booking booking, UUID callerAccountId) {
        DoctorProfile treatingDoctor = booking.getSlot().getSession().getDoctorProfile();
        if (!treatingDoctor.getAccount().getId().equals(callerAccountId)) {
            throw new ForbiddenException();
        }
        return treatingDoctor;
    }
}
