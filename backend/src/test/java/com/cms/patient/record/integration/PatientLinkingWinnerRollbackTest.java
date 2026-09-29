package com.cms.patient.record.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.patient.record.domain.Patient;
import com.cms.patient.record.service.PatientLinkingService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 066 FR-004: a concurrent call that would have created the Patient record ends in rollback, so
 * its record never persists - the call waiting behind it must still succeed, creating the record
 * itself, and exactly one row exists afterward.
 */
class PatientLinkingWinnerRollbackTest extends AbstractPatientRecordIntegrationTest {

    @Autowired
    private PatientLinkingService patientLinkingService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void waitingCallSucceedsWhenTheFirstCallRollsBack() throws Exception {
        var clinic = saveClinic("Sunrise Clinic");
        var account = savePatientAccount("jane@example.com", "9812345670");
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        CountDownLatch aLinked = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> a = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                patientLinkingService.findOrCreatePatient(account.getId(), clinic.getId(), "Jane Doe");
                aLinked.countDown();
                try {
                    releaseA.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                status.setRollbackOnly();
            }));
            assertThat(aLinked.await(30, TimeUnit.SECONDS)).isTrue();

            Future<Patient> b = executor.submit(
                    () -> patientLinkingService.findOrCreatePatient(account.getId(), clinic.getId(), "Jane Doe"));
            // Give B time to reach, and block on, A's still-open transaction before A rolls back.
            Thread.sleep(500);
            releaseA.countDown();

            a.get(30, TimeUnit.SECONDS);
            Patient created = b.get(30, TimeUnit.SECONDS);

            assertThat(created.getId()).isNotNull();
            assertThat(patientRepository.count()).isEqualTo(1);
            assertThat(patientRepository.findAll().get(0).getId()).isEqualTo(created.getId());
        } finally {
            releaseA.countDown();
            executor.shutdownNow();
        }
    }
}
