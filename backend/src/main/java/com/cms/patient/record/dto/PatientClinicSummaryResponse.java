package com.cms.patient.record.dto;

import com.cms.patient.record.Patient;
import java.util.UUID;

/** patient-booking-flow-rebuild: one row in "My clinics" - mirrors {@code ClinicMembershipResponse}'s staff-side shape. */
public record PatientClinicSummaryResponse(UUID clinicId, String name, String address) {

    public static PatientClinicSummaryResponse of(Patient patient) {
        return new PatientClinicSummaryResponse(
                patient.getClinic().getId(), patient.getClinic().getName(), patient.getClinic().getAddress());
    }
}
