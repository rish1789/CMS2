package com.cms.inbox;

import com.cms.inbox.dto.InboxItemResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 038 research.md R1/R3: push delivery via Spring's built-in {@link SseEmitter} - no new
 * dependency. In-memory, single-instance registry, an accepted v1 limitation (research.md R3); a
 * horizontally-scaled deployment would need a shared pub/sub, out of scope until the platform
 * actually needs multi-instance backend deployment (Constitution II).
 */
@Service
public class InboxBroadcastService {

    private static final long TIMEOUT_MILLIS = 30L * 60 * 1000;

    private final Map<UUID, List<SseEmitter>> emittersByClinic = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID clinicId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        List<SseEmitter> emitters = emittersByClinic.computeIfAbsent(clinicId, id -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        Runnable remove = () -> emitters.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());

        return emitter;
    }

    public void broadcast(UUID clinicId, InboxItemResponse item) {
        List<SseEmitter> emitters = emittersByClinic.get(clinicId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("inbox-item").data(item));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }
}
