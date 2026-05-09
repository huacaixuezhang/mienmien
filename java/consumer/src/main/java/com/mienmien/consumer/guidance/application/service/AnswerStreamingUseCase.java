package com.mienmien.consumer.guidance.application.service;

import com.mienmien.consumer.guidance.application.dto.StreamedAnswerPlan;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class AnswerStreamingUseCase {
    private final GuidanceApplicationService guidanceApplicationService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public AnswerStreamingUseCase(GuidanceApplicationService guidanceApplicationService) {
        this.guidanceApplicationService = guidanceApplicationService;
    }

    public SseEmitter streamAnswer(String sessionId) {
        SseEmitter emitter = new SseEmitter(60_000L);
        executor.submit(() -> {
            boolean planPrepared = false;
            try {
                StreamedAnswerPlan plan = guidanceApplicationService.prepareStreamedAnswer(sessionId);
                planPrepared = true;
                List<String> chunks = plan.chunks();
                for (int i = 0; i < chunks.size(); i++) {
                    emitter.send(SseEmitter.event().name("chunk").data(chunks.get(i)));
                    if (i < chunks.size() - 1) {
                        Thread.sleep(500);
                    }
                }
                emitter.send(SseEmitter.event().name("done").data("completed"));
                emitter.complete();
            } catch (Exception e) {
                if (!planPrepared) {
                    guidanceApplicationService.markStreamAnswerFailedIfRecoverable(sessionId);
                }
                try {
                    emitter.send(SseEmitter.event().name("fallback").data(guidanceApplicationService.streamFallbackMessage()));
                    emitter.complete();
                } catch (IOException ignored) {
                    emitter.completeWithError(e);
                }
            }
        });
        return emitter;
    }
}
