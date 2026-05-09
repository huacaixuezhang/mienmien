package com.mienmien.consumer.guidance.application.diarization.engine;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface SpeakerDiarizationEngine {
    void init(Map<String, Object> config);

    CompletableFuture<ProcessingResult> process(byte[] audioFrame);

    default void registerEnrollment(String enrollmentId, byte[] enrollmentAudio) {
        throw new UnsupportedOperationException("enrollment not supported");
    }

    void destroy();
}
