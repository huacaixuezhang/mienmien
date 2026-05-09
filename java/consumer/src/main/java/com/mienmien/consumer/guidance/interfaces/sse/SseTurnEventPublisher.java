package com.mienmien.consumer.guidance.interfaces.sse;

import com.mienmien.consumer.guidance.application.diarization.turn.TurnEvent;
import com.mienmien.consumer.guidance.application.diarization.turn.TurnEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SseTurnEventPublisher implements TurnEventPublisher {
    private static final long SSE_TIMEOUT = 60_000L;

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String sessionId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        subscribers.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(sessionId, emitter));
        emitter.onTimeout(() -> remove(sessionId, emitter));
        emitter.onError(ex -> remove(sessionId, emitter));
        return emitter;
    }

    @Override
    public void publish(TurnEvent event) {
        CopyOnWriteArrayList<SseEmitter> list = subscribers.get(event.sessionId());
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("turn_event").data(event));
            } catch (IOException e) {
                remove(event.sessionId(), emitter);
            }
        }
    }

    private void remove(String sessionId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = subscribers.get(sessionId);
        if (list != null) {
            list.remove(emitter);
        }
    }
}
