package com.mienmien.consumer.guidance.application.diarization.engine;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EnrollmentEngine implements SpeakerDiarizationEngine {
    private final Map<String, byte[]> enrollmentCache = new ConcurrentHashMap<>();

    @Override
    public void init(Map<String, Object> config) {
    }

    @Override
    public void registerEnrollment(String enrollmentId, byte[] enrollmentAudio) {
        if (enrollmentId != null && enrollmentAudio != null) {
            enrollmentCache.put(enrollmentId, enrollmentAudio);
        }
    }

    @Override
    public CompletableFuture<ProcessingResult> process(byte[] audioFrame) {
        long begin = System.currentTimeMillis();
        return CompletableFuture.supplyAsync(() -> {
            if (audioFrame == null || audioFrame.length == 0) {
                return null;
            }
            String speaker = enrollmentCache.isEmpty() ? "unknown" : enrollmentCache.keySet().iterator().next();
            return new ProcessingResult(speaker, "enroll:frame_" + audioFrame.length, 0.72d,
                    System.currentTimeMillis(), true, System.currentTimeMillis() - begin);
        });
    }

    @Override
    public void destroy() {
        enrollmentCache.clear();
    }
}
