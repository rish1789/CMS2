package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.scheduling.domain.Session;
import com.cms.scheduling.exception.TokenIssuanceFailedException;
import com.cms.scheduling.service.QueueSlotService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 067 FR-007 and FR-006: a request that cannot get a session's issuance lock within the bound is
 * refused with 503 TOKEN_ISSUANCE_FAILED and leaves no token; a request for a different session is
 * not held up by that lock at all. The lock is held here by a separate transaction, the way a
 * stalled issuance would hold it.
 */
class TokenIssuanceLockTimeoutTest extends AbstractQueueBookingIntegrationTest {

    private static final long HOLD_MS = 8_000;

    @Autowired
    private QueueSlotService queueSlotService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void aRequestWaitingPastTheBoundIsRefusedAndLeavesNoToken() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Session session = saveQueueSessionOn(clinic, doctor, LocalDate.now().plusDays(1));

        withSessionLockHeld(session.getId(), () -> {
            long start = System.nanoTime();
            assertThatThrownBy(() -> queueSlotService.issueNextSlot(session.getId()))
                    .isInstanceOf(TokenIssuanceFailedException.class);
            Duration waited = Duration.ofNanos(System.nanoTime() - start);
            assertThat(waited).isBetween(Duration.ofSeconds(4), Duration.ofSeconds(7));
        });

        assertThat(tokenCount(session)).isZero();
    }

    @Test
    void theRefusalReachesThePatientAs503TokenIssuanceFailed() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Session session = saveQueueSessionOn(clinic, doctor, LocalDate.now().plusDays(1));
        var type = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        String token = patientToken(savePatientAccount());

        withSessionLockHeld(session.getId(), () -> mockMvc.perform(
                        post("/api/v1/patients/clinics/{clinicId}/sessions/{sessionId}/queue-bookings", clinic.getId(), session.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"patientName\":\"Waiting Patient\",\"appointmentTypeId\":\"" + type.getId() + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("TOKEN_ISSUANCE_FAILED")));

        assertThat(tokenCount(session)).isZero();
        assertThat(bookingRepository.findAll()).isEmpty();
    }

    /** FR-006: holding one session's lock does not delay issuance for another session. */
    @Test
    void aDifferentSessionIsNotHeldUpByTheLock() throws Exception {
        var clinic = saveClinic();
        Session locked = saveQueueSessionOn(clinic, saveDoctorStaffedAt(clinic), LocalDate.now().plusDays(1));
        Session other = saveQueueSessionOn(clinic, saveDoctorStaffedAt(clinic), LocalDate.now().plusDays(1));

        withSessionLockHeld(locked.getId(), () -> {
            long start = System.nanoTime();
            assertThat(queueSlotService.issueNextSlot(other.getId()).getTokenNumber()).isEqualTo(1);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
        });
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /** Takes the session row lock in another thread's transaction, runs {@code action}, then lets it go. */
    private void withSessionLockHeld(UUID sessionId, ThrowingRunnable action) throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService holder = Executors.newSingleThreadExecutor();
        try {
            Future<?> holding = holder.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbcTemplate.queryForObject("SELECT id FROM session WHERE id = ? FOR NO KEY UPDATE", UUID.class, sessionId);
                locked.countDown();
                try {
                    release.await(HOLD_MS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).as("lock taken").isTrue();
            try {
                action.run();
            } finally {
                release.countDown();
                holding.get(15, TimeUnit.SECONDS);
            }
        } finally {
            holder.shutdownNow();
        }
    }

    private long tokenCount(Session session) {
        return slotRepository.findBySession_Id(session.getId()).stream()
                .map(s -> s.getTokenNumber())
                .filter(Objects::nonNull)
                .count();
    }
}
