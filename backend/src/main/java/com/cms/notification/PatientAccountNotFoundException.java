package com.cms.notification;

import java.util.UUID;

public class PatientAccountNotFoundException extends RuntimeException {

    public PatientAccountNotFoundException(UUID patientAccountId) {
        super("No Patient Account with id " + patientAccountId);
    }
}
