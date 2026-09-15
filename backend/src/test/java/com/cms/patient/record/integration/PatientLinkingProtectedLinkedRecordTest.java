package com.cms.patient.record.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.patient.record.PatientLinkingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T008: a phone match against a record already linked to a DIFFERENT account is not
 * reused - a new, separate record is created instead (AC3, FR-003's protection clause,
 * SC-006). This is the test proving the Clarify-stage privacy decision.
 */
class PatientLinkingProtectedLinkedRecordTest extends AbstractPatientRecordIntegrationTest {

    @Autowired
    private PatientLinkingService patientLinkingService;

    @Test
    void secondAccountSharingAPhoneGetsItsOwnRecordNotTheFirstAccountsLinkedOne() {
        var clinic = saveClinic("Sunrise Clinic");
        var accountX = savePatientAccount("x@example.com", "9812345670");
        var accountY = savePatientAccount("y@example.com", "9812345670");

        var recordX = patientLinkingService.findOrCreatePatient(accountX.getId(), clinic.getId(), "X's Name");
        var recordY = patientLinkingService.findOrCreatePatient(accountY.getId(), clinic.getId(), "Y's Name");

        assertThat(recordY.getId()).isNotEqualTo(recordX.getId());
        assertThat(recordY.getPatientAccount().getId()).isEqualTo(accountY.getId());
        assertThat(recordX.getPatientAccount().getId()).isEqualTo(accountX.getId());
        assertThat(patientRepository.count()).isEqualTo(2);
    }
}
