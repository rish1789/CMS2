package com.cms.booking.exception;

import java.util.UUID;

/**
 * 074-duplicate-patient-phone: a new unlinked patient's phone already belongs to an unlinked
 * patient at the same clinic ({@code uq_patient_clinic_phone_unlinked}). Names that patient when
 * known, so staff can book them instead; never links or merges anything itself. The id and name are
 * null when the conflict was only detected by the database index (a concurrent registration won).
 */
public class PatientPhoneAlreadyRegisteredException extends RuntimeException {

    private final UUID existingPatientId;
    private final String existingPatientName;

    public PatientPhoneAlreadyRegisteredException(UUID existingPatientId, String existingPatientName) {
        super("A patient with this phone number is already registered at this clinic.");
        this.existingPatientId = existingPatientId;
        this.existingPatientName = existingPatientName;
    }

    public UUID existingPatientId() {
        return existingPatientId;
    }

    public String existingPatientName() {
        return existingPatientName;
    }
}
