package com.cms.patient.record.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.patient.record.PatientLinkingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** T009: an account with no phone number never matches anything; a new record is created with a null phone (Edge Cases, FR-008). */
class PatientLinkingNoPhoneTest extends AbstractPatientRecordIntegrationTest {

    @Autowired
    private PatientLinkingService patientLinkingService;

    @Test
    void noPhoneOnAccountSkipsMatchingAndCreatesNewRecordWithNullPhone() {
        var clinic = saveClinic("Sunrise Clinic");
        saveWalkInPatient(clinic, "Unrelated Walk-In", null);
        var account = savePatientAccount("nophone@example.com", null);

        var result = patientLinkingService.findOrCreatePatient(account.getId(), clinic.getId(), "Jane Doe");

        assertThat(result.getPhone()).isNull();
        assertThat(result.getPatientAccount().getId()).isEqualTo(account.getId());
        assertThat(patientRepository.count()).isEqualTo(2);
    }
}
