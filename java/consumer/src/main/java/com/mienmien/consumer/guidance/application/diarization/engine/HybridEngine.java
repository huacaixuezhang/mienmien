package com.mienmien.consumer.guidance.application.diarization.engine;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class HybridEngine implements SpeakerDiarizationEngine {
    private final UnsupervisedEngine unsupervisedEngine;
    private final EnrollmentEngine enrollmentEngine;

    public HybridEngine(UnsupervisedEngine unsupervisedEngine, EnrollmentEngine enrollmentEngine) {
        this.unsupervisedEngine = unsupervisedEngine;
        this.enrollmentEngine = enrollmentEngine;
    }

    @Override
    public void init(Map<String, Object> config) {
        unsupervisedEngine.init(config);
        enrollmentEngine.init(config);
    }

    @Override
    public CompletableFuture<ProcessingResult> process(byte[] audioFrame) {
        return unsupervisedEngine.process(audioFrame)
                .thenCombine(enrollmentEngine.process(audioFrame), (u, e) -> {
                    if (u == null) {
                        return e;
                    }
                    if (e == null) {
                        return u;
                    }
                    return e.confidence() >= 0.7d ? e : u;
                });
    }

    @Override
    public void registerEnrollment(String enrollmentId, byte[] enrollmentAudio) {
        enrollmentEngine.registerEnrollment(enrollmentId, enrollmentAudio);
    }

    @Override
    public void destroy() {
        unsupervisedEngine.destroy();
        enrollmentEngine.destroy();
    }
}
