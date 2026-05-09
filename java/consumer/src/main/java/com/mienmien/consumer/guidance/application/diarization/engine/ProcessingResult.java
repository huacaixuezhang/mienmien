package com.mienmien.consumer.guidance.application.diarization.engine;

public record ProcessingResult(
        String speakerId,
        String text,
        double confidence,
        long timestamp,
        boolean partial,
        long processingTimeMs
) {
}
