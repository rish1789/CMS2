package com.cms.booking.service;

import com.cms.booking.exception.PatientPhoneAlreadyRegisteredException;
import com.cms.identity.clinic.Clinic;
import com.cms.patient.record.domain.Patient;
import com.cms.patient.record.repository.PatientRepository;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 074-duplicate-patient-phone (PB-001, PB-002): the one place staff fixed-time booking, staff queue
 * booking and front-desk walk-in create an unlinked patient. Each used a plain {@code save}, so a
 * same-clinic phone collision surfaced later - inside the slot-race handler as "slot already
 * booked", or unhandled as a 500.
 *
 * <p>Checks first and names the existing record; then flushes the INSERT right here, so a
 * concurrent registration that slipped past the check is caught on the index and reported the same
 * way. It never re-queries after a failed INSERT - PostgreSQL has already aborted the transaction
 * (066 research.md R1) - and the caller's transaction rolls back, leaving no booking or token.
 */
// A plain helper, not a bean: each booking service builds it from the PatientRepository it already
// has, so the services' constructors (and the unit tests that call them) are unchanged.
public class WalkInPatientRegistrar {

    static final String UNLINKED_PHONE_INDEX = "uq_patient_clinic_phone_unlinked";

    private final PatientRepository patientRepository;

    public WalkInPatientRegistrar(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    /** {@code phone} null means none was given - the partial index ignores NULL, so there is nothing to check. */
    public Patient register(Clinic clinic, String name, String phone, String email) {
        if (phone != null) {
            patientRepository
                    .findByClinic_IdAndPhoneAndPatientAccountIsNull(clinic.getId(), phone)
                    .ifPresent(existing -> {
                        throw new PatientPhoneAlreadyRegisteredException(existing.getId(), existing.getName());
                    });
        }
        try {
            return patientRepository.saveAndFlush(new Patient(clinic, null, name, phone, email));
        } catch (DataIntegrityViolationException e) {
            if (violates(e, UNLINKED_PHONE_INDEX)) {
                throw new PatientPhoneAlreadyRegisteredException(null, null);
            }
            throw e;
        }
    }

    private static boolean violates(Throwable error, String constraint) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException violation
                    && constraint.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
            if (t.getMessage() != null && t.getMessage().contains("\"" + constraint + "\"")) {
                return true;
            }
        }
        return false;
    }
}
