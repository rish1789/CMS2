package com.cms.patient.record;

import java.util.UUID;

public class PatientAccountNotFoundException extends RuntimeException {

    public PatientAccountNotFoundException(UUID patientAccountId) {
        super("No Patient Account with id " + patientAccountId);
    }
}
