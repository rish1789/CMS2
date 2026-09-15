package com.cms.clinical;

import com.cms.booking.Booking;
import com.cms.identity.doctor.DoctorProfile;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 036: no update/delete method exists anywhere in this class - immutability is structural,
 * mirrors 030/031. Reuses {@link TreatingDoctorAuthorizationService} unchanged as its third
 * caller (research.md R2) - no data-layer uniqueness guard needed, a booking may carry any
 * number of independent references (research.md, mirrors 031's own reasoning).
 */
@Service
public class ExternalRecordReferenceService {

    private final TreatingDoctorAuthorizationService treatingDoctorAuthorizationService;
    private final ExternalRecordReferenceRepository externalRecordReferenceRepository;

    public ExternalRecordReferenceService(
            TreatingDoctorAuthorizationService treatingDoctorAuthorizationService,
            ExternalRecordReferenceRepository externalRecordReferenceRepository) {
        this.treatingDoctorAuthorizationService = treatingDoctorAuthorizationService;
        this.externalRecordReferenceRepository = externalRecordReferenceRepository;
    }

    /** FR-001/FR-003 (research.md R2/R3). */
    @Transactional
    public ExternalRecordReference create(
            UUID clinicId,
            UUID bookingId,
            UUID callerAccountId,
            String recordType,
            String sourceProvider,
            LocalDate recordDate,
            String summary) {
        Booking booking = treatingDoctorAuthorizationService.findBookingInClinic(clinicId, bookingId);
        DoctorProfile treatingDoctor = treatingDoctorAuthorizationService.requireTreatingDoctor(booking, callerAccountId);

        return externalRecordReferenceRepository.save(
                new ExternalRecordReference(booking, treatingDoctor, recordType, sourceProvider, recordDate, summary));
    }

    /** FR-007: every reference the treating doctor has authored for this booking - an empty list is a valid success. */
    @Transactional(readOnly = true)
    public List<ExternalRecordReference> list(UUID clinicId, UUID bookingId, UUID callerAccountId) {
        Booking booking = treatingDoctorAuthorizationService.findBookingInClinic(clinicId, bookingId);
        treatingDoctorAuthorizationService.requireTreatingDoctor(booking, callerAccountId);

        return externalRecordReferenceRepository.findByBooking_Id(bookingId);
    }
}
