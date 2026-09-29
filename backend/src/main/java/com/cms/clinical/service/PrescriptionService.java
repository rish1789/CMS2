package com.cms.clinical.service;

import com.cms.clinical.domain.Prescription;
import com.cms.clinical.exception.PrescriptionItemRequiredException;
import com.cms.clinical.repository.PrescriptionRepository;


import com.cms.booking.domain.Booking;
import com.cms.booking.exception.BookingNotFoundException;
import com.cms.booking.repository.BookingRepository;
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
    private final BookingRepository bookingRepository;

    public PrescriptionService(
            TreatingDoctorAuthorizationService treatingDoctorAuthorizationService,
            PrescriptionRepository prescriptionRepository,
            BookingRepository bookingRepository) {
        this.treatingDoctorAuthorizationService = treatingDoctorAuthorizationService;
        this.prescriptionRepository = prescriptionRepository;
        this.bookingRepository = bookingRepository;
    }

    /** FR-001/FR-003/FR-006 (research.md R4): rejects an empty item list before any write. */
    @Transactional
    public Prescription create(
            UUID clinicId, UUID bookingId, UUID callerAccountId, List<PrescriptionItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new PrescriptionItemRequiredException();
        }

        Booking booking = treatingDoctorAuthorizationService.findBookingInClinic(clinicId, bookingId);
        DoctorProfile treatingDoctor = treatingDoctorAuthorizationService.requireActiveTreatingDoctor(booking, callerAccountId);

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

    /**
     * 059-patient-clinical-record-access FR-002 (research.md Decision 1/2): a patient reads every
     * prescription for one of their own bookings - the exact same {@code findByBooking_Id} lookup
     * {@link #list} above already uses. An empty list is a valid, successful result (research.md
     * Decision 5), mirroring {@link #list}'s own existing "empty list is valid" contract.
     */
    @Transactional(readOnly = true)
    public List<Prescription> listForPatient(UUID bookingId, UUID patientAccountId) {
        bookingRepository
                .findByIdAndPatient_PatientAccount_Id(bookingId, patientAccountId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        return prescriptionRepository.findByBooking_Id(bookingId);
    }
}
