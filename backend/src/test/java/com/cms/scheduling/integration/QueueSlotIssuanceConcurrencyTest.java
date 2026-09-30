package com.cms.scheduling.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * 019 FR-006, spec US1 AC4: concurrent issuance for the same Session never duplicates or skips a token.
 * 067 (SC-001/SC-002/SC-006): repeated 10 times with 20 callers each - the retry-based issuance
 * this replaced passed a single round most of the time and failed intermittently (PB-003).
 */
class QueueSlotIssuanceConcurrencyTest extends AbstractQueueSlotIntegrationTest {

    private static final int ROUNDS = 10;
    private static final int CALLS = 20;

    @Test
    void twentyConcurrentCallsEachReceiveADistinctSequentialToken() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        // One schedule; generation covers a 15-day horizon, so each round uses that schedule's
        // untouched session on a later date.
        var scheduleId = saveQueueSessionOn(clinic, doctor, tomorrow).getSchedule().getId();
        var sessions = sessionRepository.findBySchedule_Id(scheduleId);

        ExecutorService executor = Executors.newFixedThreadPool(10);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                LocalDate date = tomorrow.plusDays(round);
                var session = sessions.stream()
                        .filter(s -> s.getSessionDate().equals(date))
                        .findFirst()
                        .orElseThrow();
                List<Callable<Integer>> calls = IntStream.range(0, CALLS)
                        .<Callable<Integer>>mapToObj(i -> () -> queueSlotService.issueNextSlot(session.getId()).getTokenNumber())
                        .toList();

                List<Integer> tokens = new ArrayList<>();
                for (Future<Integer> future : executor.invokeAll(calls)) {
                    tokens.add(future.get());
                }

                assertThat(tokens)
                        .as("round %d", round)
                        .containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, CALLS).boxed().toList());
            }
        } finally {
            executor.shutdown();
        }
    }
}
