package com.cms.patient.record.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.patient.record.PatientLinkingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** T006: a phone-matching, unlinked walk-in record is reused and linked, prior data preserved (AC1, FR-003, SC-001). */
class PatientLinkingPhoneMatchTest extends AbstractPatientRecordIntegrationTest {

    @Autowired
    private PatientLinkingService patientLinkingService;

    @Test
    void matchingWalkInRecordIsReusedAndLinked() {
        var clinic = saveClinic("Sunrise Clinic");
        var walkIn = saveWalkInPatient(clinic, "Priya Nair", "9812345670");
        var account = savePatientAccount("priya@example.com", "9812345670");

        var result = patientLinkingService.findOrCreatePatient(account.getId(), clinic.getId(), "Ignored Name");

        assertThat(result.getId()).isEqualTo(walkIn.getId());
        assertThat(result.getName()).isEqualTo("Priya Nair");
        assertThat(result.getPatientAccount().getId()).isEqualTo(account.getId());
        assertThat(patientRepository.count()).isEqualTo(1);
    }
}
