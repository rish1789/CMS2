package com.cms.patient.record.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.patient.record.PatientLinkingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T013: an account already linked at a clinic gets the same record back on a repeat
 * call, no re-matching, no new row - even if a different name argument is passed
 * (AC2, FR-002, SC-003).
 */
class PatientLinkingReuseExistingLinkTest extends AbstractPatientRecordIntegrationTest {

    @Autowired
    private PatientLinkingService patientLinkingService;

    @Test
    void repeatedCallForAlreadyLinkedAccountReusesTheSameRecord() {
        var clinic = saveClinic("Sunrise Clinic");
        var account = savePatientAccount("jane@example.com", "9812345670");

        var first = patientLinkingService.findOrCreatePatient(account.getId(), clinic.getId(), "Jane Doe");
        var second = patientLinkingService.findOrCreatePatient(account.getId(), clinic.getId(), "Different Name");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getName()).isEqualTo("Jane Doe");
        assertThat(patientRepository.count()).isEqualTo(1);
    }
}
