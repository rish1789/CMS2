package com.cms.patient.record.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cms.patient.record.Patient;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * T005: proves V5's uq_patient_clinic_phone_unlinked constraint directly, since this
 * feature's own service never writes an unlinked row (research.md decision #1) - no
 * user story exercises this path, so it needs its own proof (Constitution Principle I).
 */
class PatientTableUnlinkedPhoneUniquenessTest extends AbstractPatientRecordIntegrationTest {

    @Test
    void secondUnlinkedRowWithSameClinicAndPhoneIsRejected() {
        var clinic = saveClinic("Sunrise Clinic");
        saveWalkInPatient(clinic, "First Walk-In", "9812345670");

        assertThatThrownBy(() -> patientRepository.saveAndFlush(new Patient(clinic, null, "Second Walk-In", "9812345670")))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(patientRepository.count()).isEqualTo(1);
    }

    @Test
    void secondUnlinkedRowWithSameClinicButDifferentPhoneSucceeds() {
        var clinic = saveClinic("Sunrise Clinic");
        saveWalkInPatient(clinic, "First Walk-In", "9812345670");

        patientRepository.saveAndFlush(new Patient(clinic, null, "Second Walk-In", "9812345671"));

        assertThat(patientRepository.count()).isEqualTo(2);
    }
}
