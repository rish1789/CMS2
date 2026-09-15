package com.cms.patient.record.dto;

import com.cms.patient.record.Patient;
import java.util.UUID;

public record PatientSearchResultResponse(UUID patientId, String name, String phone) {

    public static PatientSearchResultResponse from(Patient patient) {
        return new PatientSearchResultResponse(patient.getId(), patient.getName(), patient.getPhone());
    }
}
