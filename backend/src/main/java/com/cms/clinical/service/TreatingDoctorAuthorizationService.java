package com.cms.clinical.service;

import com.cms.clinical.exception.ForbiddenException;


import com.cms.booking.domain.Booking;
import com.cms.booking.exception.BookingNotFoundException;
import com.cms.booking.repository.BookingRepository;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
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
    private final RoleAssignmentRepository roleAssignmentRepository;

    public TreatingDoctorAuthorizationService(
            BookingRepository bookingRepository, RoleAssignmentRepository roleAssignmentRepository) {
        this.bookingRepository = bookingRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
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

    /**
     * 065-phase1-stabilization (SEC-06 / PB-008): the check for <em>creating</em> new clinical
     * documentation. Same treating-doctor identity rule as {@link #requireTreatingDoctor}, plus the
     * doctor must still hold an active Doctor role at the booking's clinic - a doctor whose role
     * there was deactivated can no longer add records for that clinic's patients. Reading
     * previously written documentation keeps using {@link #requireTreatingDoctor} (spec 034's
     * historical visibility, unchanged).
     */
    @Transactional(readOnly = true)
    public DoctorProfile requireActiveTreatingDoctor(Booking booking, UUID callerAccountId) {
        DoctorProfile treatingDoctor = requireTreatingDoctor(booking, callerAccountId);
        UUID clinicId = booking.getSlot().getSession().getClinic().getId();
        if (!roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                callerAccountId, clinicId, RoleAssignment.Role.Doctor)) {
            throw new ForbiddenException();
        }
        return treatingDoctor;
    }
}
