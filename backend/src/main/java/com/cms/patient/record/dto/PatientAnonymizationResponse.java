package com.cms.patient.record.dto;

import com.cms.patient.record.Patient;
import java.time.Instant;
import java.util.UUID;

public record PatientAnonymizationResponse(UUID patientId, boolean anonymized, Instant anonymizedAt) {

    public static PatientAnonymizationResponse of(Patient patient) {
        return new PatientAnonymizationResponse(patient.getId(), patient.isAnonymized(), patient.getAnonymizedAt());
    }
}
