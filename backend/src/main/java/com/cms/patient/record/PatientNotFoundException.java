package com.cms.patient.record;

import java.util.UUID;

/** 037 research.md R3: a fresh exception, not reused from com.cms.booking (which already depends on this module, not the reverse). */
public class PatientNotFoundException extends RuntimeException {

    public PatientNotFoundException(UUID patientId) {
        super("No Patient with id " + patientId + " at this clinic");
    }
}
