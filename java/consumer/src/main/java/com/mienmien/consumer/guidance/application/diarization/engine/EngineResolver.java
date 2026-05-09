package com.mienmien.consumer.guidance.application.diarization.engine;

public interface EngineResolver {
    SpeakerDiarizationEngine resolve(String mode);
}
