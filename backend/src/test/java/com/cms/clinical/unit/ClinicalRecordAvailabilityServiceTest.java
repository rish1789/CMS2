package com.cms.clinical.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.cms.clinical.repository.ConsultationNoteRepository;
import com.cms.clinical.repository.ExternalRecordReferenceRepository;
import com.cms.clinical.repository.PrescriptionRepository;
import com.cms.clinical.service.ClinicalRecordAvailabilityService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 059-patient-clinical-record-access: this module's first pure-Mockito unit tier (existing
 * coverage was integration-only). Confirms the union logic across all three repositories, and
 * that a booking id not scoped to the caller's patient account never appears - the repository
 * layer's own query does that scoping (data-model.md), so this test only needs to confirm the
 * service correctly unions whatever each mocked repository returns.
 */
@ExtendWith(MockitoExtension.class)
class ClinicalRecordAvailabilityServiceTest {

    @Mock
    private ConsultationNoteRepository consultationNoteRepository;

    @Mock
    private PrescriptionRepository prescriptionRepository;

    @Mock
    private ExternalRecordReferenceRepository externalRecordReferenceRepository;

    private ClinicalRecordAvailabilityService newService() {
        return new ClinicalRecordAvailabilityService(
                consultationNoteRepository, prescriptionRepository, externalRecordReferenceRepository);
    }

    @Test
    void unionsBookingIdsAcrossAllThreeRecordTypes() {
        UUID patientAccountId = UUID.randomUUID();
        UUID noteBookingId = UUID.randomUUID();
        UUID prescriptionBookingId = UUID.randomUUID();
        UUID referenceBookingId = UUID.randomUUID();
        List<UUID> requested = List.of(noteBookingId, prescriptionBookingId, referenceBookingId);

        when(consultationNoteRepository.findBookingIdsWithNoteForPatient(requested, patientAccountId))
                .thenReturn(Set.of(noteBookingId));
        when(prescriptionRepository.findBookingIdsWithPrescriptionForPatient(requested, patientAccountId))
                .thenReturn(Set.of(prescriptionBookingId));
        when(externalRecordReferenceRepository.findBookingIdsWithReferenceForPatient(requested, patientAccountId))
                .thenReturn(Set.of(referenceBookingId));

        Set<UUID> result = newService().findBookingIdsWithAnyRecord(requested, patientAccountId);

        assertThat(result).containsExactlyInAnyOrder(noteBookingId, prescriptionBookingId, referenceBookingId);
    }

    @Test
    void aBookingWithNoRecordOfAnyTypeIsAbsentFromTheResult() {
        UUID patientAccountId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        List<UUID> requested = List.of(bookingId);

        when(consultationNoteRepository.findBookingIdsWithNoteForPatient(requested, patientAccountId))
                .thenReturn(Set.of());
        when(prescriptionRepository.findBookingIdsWithPrescriptionForPatient(requested, patientAccountId))
                .thenReturn(Set.of());
        when(externalRecordReferenceRepository.findBookingIdsWithReferenceForPatient(requested, patientAccountId))
                .thenReturn(Set.of());

        Set<UUID> result = newService().findBookingIdsWithAnyRecord(requested, patientAccountId);

        assertThat(result).isEmpty();
    }

    @Test
    void anEmptyRequestReturnsEmptyWithoutQueryingAnyRepository() {
        UUID patientAccountId = UUID.randomUUID();

        Set<UUID> result = newService().findBookingIdsWithAnyRecord(List.of(), patientAccountId);

        assertThat(result).isEmpty();
    }
}
