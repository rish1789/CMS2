package com.cms.booking;

import java.util.UUID;

public class PatientNotFoundException extends RuntimeException {

    public PatientNotFoundException(UUID patientId) {
        super("No Patient with id " + patientId + " at this clinic");
    }
}
