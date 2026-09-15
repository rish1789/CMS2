package com.cms.patient.record;

import java.util.UUID;

/** FR-003: anonymization is blocked while this patient has at least one active future booking. */
public class PatientHasActiveFutureBookingException extends RuntimeException {

    public PatientHasActiveFutureBookingException(UUID patientId) {
        super("Patient " + patientId + " has at least one active future booking");
    }
}
