package com.mienmien.consumer.guidance.application.diarization.turn;

public record TurnEvent(
        String sessionId,
        TurnType type,
        String speaker,
        String text,
        long timestamp,
        double confidence
) {
}
