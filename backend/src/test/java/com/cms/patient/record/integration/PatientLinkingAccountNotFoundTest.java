package com.cms.patient.record.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cms.patient.record.PatientAccountNotFoundException;
import com.cms.patient.record.PatientLinkingService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** T010: an unknown patientAccountId throws PatientAccountNotFoundException. */
class PatientLinkingAccountNotFoundTest extends AbstractPatientRecordIntegrationTest {

    @Autowired
    private PatientLinkingService patientLinkingService;

    @Test
    void unknownPatientAccountIdThrows() {
        var clinic = saveClinic("Sunrise Clinic");

        assertThatThrownBy(() -> patientLinkingService.findOrCreatePatient(UUID.randomUUID(), clinic.getId(), "Jane Doe"))
                .isInstanceOf(PatientAccountNotFoundException.class);
    }
}
