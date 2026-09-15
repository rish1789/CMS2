package com.cms.patient.record.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.patient.record.PatientLinkingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** T012: no matching record exists: a new Patient record is created, scoped, linked (AC1, FR-004, SC-002). */
class PatientLinkingNewRecordTest extends AbstractPatientRecordIntegrationTest {

    @Autowired
    private PatientLinkingService patientLinkingService;

    @Test
    void noMatchCreatesNewScopedLinkedRecord() {
        var clinic = saveClinic("Sunrise Clinic");
        var account = savePatientAccount("jane@example.com", "9812345670");

        var result = patientLinkingService.findOrCreatePatient(account.getId(), clinic.getId(), "Jane Doe");

        assertThat(result.getClinic().getId()).isEqualTo(clinic.getId());
        assertThat(result.getPatientAccount().getId()).isEqualTo(account.getId());
        assertThat(result.getName()).isEqualTo("Jane Doe");
        assertThat(result.getPhone()).isEqualTo("9812345670");
        assertThat(patientRepository.count()).isEqualTo(1);
    }
}
