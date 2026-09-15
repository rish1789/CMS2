package com.cms.patient.record.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.patient.record.PatientLinkingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** T007: the same account's Patient records at two different clinics are independent (AC2, FR-007, SC-005). */
class PatientLinkingClinicScopeIndependenceTest extends AbstractPatientRecordIntegrationTest {

    @Autowired
    private PatientLinkingService patientLinkingService;

    @Test
    void recordsAtDifferentClinicsAreIndependent() {
        var clinicA = saveClinic("Sunrise Clinic");
        var clinicB = saveClinic("Riverside Clinic");
        var account = savePatientAccount("patient@example.com", "9812345670");

        var patientAtA = patientLinkingService.findOrCreatePatient(account.getId(), clinicA.getId(), "Jane Doe");
        var patientAtB = patientLinkingService.findOrCreatePatient(account.getId(), clinicB.getId(), "Jane Doe");

        assertThat(patientAtA.getId()).isNotEqualTo(patientAtB.getId());
        assertThat(patientAtA.getClinic().getId()).isEqualTo(clinicA.getId());
        assertThat(patientAtB.getClinic().getId()).isEqualTo(clinicB.getId());
        assertThat(patientRepository.count()).isEqualTo(2);
    }
}
