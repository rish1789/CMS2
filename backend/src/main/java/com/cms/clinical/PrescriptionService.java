package com.cms.clinical;

import com.cms.booking.Booking;
import com.cms.clinical.dto.PrescriptionItemRequest;
import com.cms.identity.doctor.DoctorProfile;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 035: no update/delete method exists anywhere in this class (research.md, mirrors 034's own
 * R7). Unlike {@link ConsultationNoteService}, no data-layer uniqueness guard is needed here -
 * a booking may carry any number of independent Prescriptions (research.md R3).
 */
@Service
public class PrescriptionService {

    private final TreatingDoctorAuthorizationService treatingDoctorAuthorizationService;
    private final PrescriptionRepository prescriptionRepository;

    public PrescriptionService(
            TreatingDoctorAuthorizationService treatingDoctorAuthorizationService,
            PrescriptionRepository prescriptionRepository) {
        this.treatingDoctorAuthorizationService = treatingDoctorAuthorizationService;
        this.prescriptionRepository = prescriptionRepository;
    }

    /** FR-001/FR-003/FR-006 (research.md R4): rejects an empty item list before any write. */
    @Transactional
    public Prescription create(
            UUID clinicId, UUID bookingId, UUID callerAccountId, List<PrescriptionItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new PrescriptionItemRequiredException();
        }

        Booking booking = treatingDoctorAuthorizationService.findBookingInClinic(clinicId, bookingId);
        DoctorProfile treatingDoctor = treatingDoctorAuthorizationService.requireTreatingDoctor(booking, callerAccountId);

        Prescription prescription = new Prescription(booking, treatingDoctor);
        for (PrescriptionItemRequest item : items) {
            prescription.addItem(item.medicationName(), item.dosage(), item.frequency(), item.duration(), item.instructions());
        }

        return prescriptionRepository.save(prescription);
    }

    /** FR-008: every Prescription the treating doctor has authored for this booking - an empty list is a valid success (research.md R3). */
    @Transactional(readOnly = true)
    public List<Prescription> list(UUID clinicId, UUID bookingId, UUID callerAccountId) {
        Booking booking = treatingDoctorAuthorizationService.findBookingInClinic(clinicId, bookingId);
        treatingDoctorAuthorizationService.requireTreatingDoctor(booking, callerAccountId);

        return prescriptionRepository.findByBooking_Id(bookingId);
    }
}
