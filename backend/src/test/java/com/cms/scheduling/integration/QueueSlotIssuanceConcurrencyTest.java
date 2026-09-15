package com.cms.scheduling.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** 019 FR-006, spec US1 AC4: concurrent issuance for the same Session never duplicates or skips a token. */
class QueueSlotIssuanceConcurrencyTest extends AbstractQueueSlotIntegrationTest {

    @Test
    void twentyConcurrentCallsEachReceiveADistinctSequentialToken() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveQueueSession(clinic, doctor);

        int callCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        try {
            List<Callable<Integer>> calls = IntStream.range(0, callCount)
                    .<Callable<Integer>>mapToObj(i -> () -> queueSlotService.issueNextSlot(session.getId()).getTokenNumber())
                    .toList();

            List<Future<Integer>> futures = executor.invokeAll(calls);
            List<Integer> tokens = new java.util.ArrayList<>();
            for (Future<Integer> future : futures) {
                tokens.add(future.get());
            }

            assertThat(tokens).hasSize(callCount);
            assertThat(tokens).doesNotHaveDuplicates();
            assertThat(tokens.stream().sorted().collect(Collectors.toList()))
                    .containsExactlyElementsOf(IntStream.rangeClosed(1, callCount).boxed().toList());
        } finally {
            executor.shutdown();
        }
    }
}
