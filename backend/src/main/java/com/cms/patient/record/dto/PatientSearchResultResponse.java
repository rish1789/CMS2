package com.cms.patient.record.dto;

import com.cms.patient.record.domain.Patient;
import java.time.Instant;
import java.util.UUID;

// 052-patient-clinical-hub T006 (data-model.md Decision 3): anonymizedAt added - previously
// omitted from every response using this shared record, leaving the frontend with no way to
// detect an already-anonymized patient outside the instant right after the anonymize action
// itself succeeds.
//
// patientAccountId added 2026-09-16: StaffJoinWaitlistForm's "Patient account" field was a raw
// free-text UUID input with no picker (the exact bug class PatientPicker/this search endpoint
// was built to eliminate for WalkInForm/BookSlotForm, just missed here) - a malformed value
// (e.g. a typed name) fell through every specific exception handler to ApiErrorController's
// generic "The request could not be processed." fallback. Wiring StaffJoinWaitlistForm onto
// this same search+picker needs a way to resolve the clinic-scoped Patient a staff member finds
// by name/phone back to the PatientAccount the waitlist endpoint actually requires (Patient.
// patientAccount is nullable - not every clinic-scoped Patient has a linked account).
public record PatientSearchResultResponse(
        UUID patientId, String name, String phone, Instant anonymizedAt, UUID patientAccountId) {

    public static PatientSearchResultResponse from(Patient patient) {
        return new PatientSearchResultResponse(
                patient.getId(),
                patient.getName(),
                patient.getPhone(),
                patient.getAnonymizedAt(),
                patient.getPatientAccount() != null ? patient.getPatientAccount().getId() : null);
    }
}
