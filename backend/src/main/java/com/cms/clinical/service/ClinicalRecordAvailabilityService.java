package com.cms.clinical.service;

import com.cms.clinical.repository.ConsultationNoteRepository;
import com.cms.clinical.repository.ExternalRecordReferenceRepository;
import com.cms.clinical.repository.PrescriptionRepository;


import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 059-patient-clinical-record-access FR-004 (data-model.md): of a patient's own booking ids,
 * which have at least one clinical record (a consultation note, a prescription, or an external
 * record reference)? Lives in {@code clinical}, not {@code booking}, specifically so
 * {@code PatientBookingSummaryResponse} never needs a reverse dependency into this module
 * (research.md Decision 3) - {@code clinical} already depends on {@code booking}, and adding the
 * other direction would create a cycle.
 */
@Service
public class ClinicalRecordAvailabilityService {

    private final ConsultationNoteRepository consultationNoteRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final ExternalRecordReferenceRepository externalRecordReferenceRepository;

    public ClinicalRecordAvailabilityService(
            ConsultationNoteRepository consultationNoteRepository,
            PrescriptionRepository prescriptionRepository,
            ExternalRecordReferenceRepository externalRecordReferenceRepository) {
        this.consultationNoteRepository = consultationNoteRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.externalRecordReferenceRepository = externalRecordReferenceRepository;
    }

    @Transactional(readOnly = true)
    public Set<UUID> findBookingIdsWithAnyRecord(Collection<UUID> bookingIds, UUID patientAccountId) {
        Set<UUID> result = new HashSet<>();
        if (bookingIds == null || bookingIds.isEmpty()) {
            return result;
        }
        result.addAll(consultationNoteRepository.findBookingIdsWithNoteForPatient(bookingIds, patientAccountId));
        result.addAll(prescriptionRepository.findBookingIdsWithPrescriptionForPatient(bookingIds, patientAccountId));
        result.addAll(
                externalRecordReferenceRepository.findBookingIdsWithReferenceForPatient(bookingIds, patientAccountId));
        return result;
    }
}
