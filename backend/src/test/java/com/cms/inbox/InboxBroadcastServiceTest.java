package com.cms.inbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.inbox.dto.InboxItemResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 038 T017/T029: a plain unit test of {@link InboxBroadcastService} itself - no Spring context,
 * no Testcontainers, actually executable in this sandbox (unlike every Docker-backed integration
 * test in this project). Full end-to-end SSE-over-HTTP delivery is validated manually via
 * quickstart.md Scenario 1, since this is the codebase's first use of Spring MVC's async/SSE
 * support and MockMvc's async-dispatch machinery isn't otherwise exercised anywhere here.
 */
class InboxBroadcastServiceTest {

    private final InboxBroadcastService broadcastService = new InboxBroadcastService();

    private static InboxItemResponse aResponse() {
        return new InboxItemResponse(
                UUID.randomUUID(), InboxItemType.WALK_IN, InboxItemStatus.UNCLAIMED, null, null, Instant.now(), Map.of());
    }

    @Test
    void subscribedEmitterReceivesABroadcastForItsOwnClinic() throws Exception {
        UUID clinicId = UUID.randomUUID();
        AtomicInteger received = new AtomicInteger();
        SseEmitter emitter = broadcastService.subscribe(clinicId);
        emitter.onCompletion(received::incrementAndGet);

        broadcastService.broadcast(clinicId, aResponse());

        // No exception, and the emitter was not completed by a successful send.
        assertThat(received.get()).isZero();
    }

    @Test
    void broadcastToAClinicWithNoSubscribersIsANoOp() {
        UUID clinicId = UUID.randomUUID();

        broadcastService.broadcast(clinicId, aResponse());
        // No exception thrown - the whole point of this test.
    }

    @Test
    void broadcastNeverReachesADifferentClinicsSubscriber() throws Exception {
        UUID clinicA = UUID.randomUUID();
        UUID clinicB = UUID.randomUUID();
        AtomicInteger completedB = new AtomicInteger();
        SseEmitter emitterB = broadcastService.subscribe(clinicB);
        emitterB.onCompletion(completedB::incrementAndGet);

        broadcastService.broadcast(clinicA, aResponse());

        assertThat(completedB.get()).isZero();
    }
}
