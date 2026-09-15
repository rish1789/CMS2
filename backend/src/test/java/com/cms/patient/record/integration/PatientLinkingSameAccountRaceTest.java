package com.cms.patient.record.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.patient.record.Patient;
import com.cms.patient.record.PatientLinkingService;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T014: two concurrent calls for the same account+clinic (no pre-existing record) both
 * succeed, return the same Patient id, and exactly one row exists afterward
 * (AC1, FR-005a/FR-006, SC-004).
 */
class PatientLinkingSameAccountRaceTest extends AbstractPatientRecordIntegrationTest {

    @Autowired
    private PatientLinkingService patientLinkingService;

    @Test
    void concurrentCallsForSameAccountAndClinicNeverDuplicate() throws Exception {
        var clinic = saveClinic("Sunrise Clinic");
        var account = savePatientAccount("jane@example.com", "9812345670");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Patient> submit =
                    () -> patientLinkingService.findOrCreatePatient(account.getId(), clinic.getId(), "Jane Doe");

            List<Future<Patient>> results = executor.invokeAll(List.of(submit, submit));
            Patient first = results.get(0).get(30, TimeUnit.SECONDS);
            Patient second = results.get(1).get(30, TimeUnit.SECONDS);

            assertThat(first.getId()).isEqualTo(second.getId());
            assertThat(patientRepository.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
