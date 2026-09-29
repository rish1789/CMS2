package com.cms.clinical.api;

import com.cms.clinical.dto.ConsultationNoteResponse;
import com.cms.clinical.dto.ExternalRecordReferenceResponse;
import com.cms.clinical.dto.PrescriptionResponse;
import com.cms.clinical.exception.BookingIdsRequiredException;
import com.cms.clinical.service.ClinicalRecordAvailabilityService;
import com.cms.clinical.service.ConsultationNoteService;
import com.cms.clinical.service.ExternalRecordReferenceService;
import com.cms.clinical.service.PrescriptionService;
import com.cms.patient.account.config.SecurityConfig;


import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 059-patient-clinical-record-access: the patient-realm read surface for the three existing
 * clinical documentation types (Consultation Note, Prescription, External Record Reference),
 * strictly additive alongside the staff-side {@code Staff*Controller}s in this same package -
 * those are entirely untouched (spec.md FR-007/FR-008). Each per-type endpoint here is added by
 * its own user story; this file starts with only the cross-type availability check (FR-004),
 * which every story's frontend work depends on.
 */
@RestController
public class PatientClinicalRecordController {

    private final ClinicalRecordAvailabilityService clinicalRecordAvailabilityService;
    private final ConsultationNoteService consultationNoteService;
    private final PrescriptionService prescriptionService;
    private final ExternalRecordReferenceService externalRecordReferenceService;

    public PatientClinicalRecordController(
            ClinicalRecordAvailabilityService clinicalRecordAvailabilityService,
            ConsultationNoteService consultationNoteService,
            PrescriptionService prescriptionService,
            ExternalRecordReferenceService externalRecordReferenceService) {
        this.clinicalRecordAvailabilityService = clinicalRecordAvailabilityService;
        this.consultationNoteService = consultationNoteService;
        this.prescriptionService = prescriptionService;
        this.externalRecordReferenceService = externalRecordReferenceService;
    }

    /** FR-004: which of the caller's own booking ids have at least one clinical record - one bulk call, not one per row. */
    @GetMapping("/api/v1/patients/bookings/clinical-record-availability")
    public AvailabilityResponse availability(
            @RequestParam(required = false) List<UUID> bookingIds, Authentication authentication) {
        if (bookingIds == null || bookingIds.isEmpty()) {
            throw new BookingIdsRequiredException();
        }
        UUID patientAccountId = SecurityConfig.currentPatientAccountId(authentication);
        Set<UUID> withRecords =
                clinicalRecordAvailabilityService.findBookingIdsWithAnyRecord(bookingIds, patientAccountId);
        return new AvailabilityResponse(withRecords);
    }

    public record AvailabilityResponse(Set<UUID> bookingIdsWithRecords) {}

    /** FR-001: 200 with the note when one exists, 200 with a null body when it doesn't (research.md Decision 5). */
    @GetMapping("/api/v1/patients/bookings/{bookingId}/consultation-note")
    public ConsultationNoteResponse consultationNote(@PathVariable UUID bookingId, Authentication authentication) {
        UUID patientAccountId = SecurityConfig.currentPatientAccountId(authentication);
        return consultationNoteService
                .getForPatient(bookingId, patientAccountId)
                .map(ConsultationNoteResponse::of)
                .orElse(null);
    }

    /** FR-002: every prescription for one of the caller's own bookings - an empty array is a valid success (research.md Decision 5). */
    @GetMapping("/api/v1/patients/bookings/{bookingId}/prescriptions")
    public List<PrescriptionResponse> prescriptions(@PathVariable UUID bookingId, Authentication authentication) {
        UUID patientAccountId = SecurityConfig.currentPatientAccountId(authentication);
        return prescriptionService.listForPatient(bookingId, patientAccountId).stream()
                .map(PrescriptionResponse::of)
                .toList();
    }

    /** FR-003: every external record reference for one of the caller's own bookings - an empty array is a valid success (research.md Decision 5). */
    @GetMapping("/api/v1/patients/bookings/{bookingId}/external-record-references")
    public List<ExternalRecordReferenceResponse> externalRecordReferences(
            @PathVariable UUID bookingId, Authentication authentication) {
        UUID patientAccountId = SecurityConfig.currentPatientAccountId(authentication);
        return externalRecordReferenceService.listForPatient(bookingId, patientAccountId).stream()
                .map(ExternalRecordReferenceResponse::of)
                .toList();
    }
}
