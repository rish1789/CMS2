package com.cms.notification.exception;



import java.util.UUID;

public class PatientAccountNotFoundException extends RuntimeException {

    public PatientAccountNotFoundException(UUID patientAccountId) {
        super("No Patient Account with id " + patientAccountId);
    }
}
