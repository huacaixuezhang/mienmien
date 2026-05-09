package com.mienmien.consumer.guidance.application.diarization.engine;

import org.springframework.stereotype.Component;

@Component
public class EngineRegistryResolver implements EngineResolver {
    private final UnsupervisedEngine unsupervisedEngine;
    private final EnrollmentEngine enrollmentEngine;
    private final HybridEngine hybridEngine;

    public EngineRegistryResolver(
            UnsupervisedEngine unsupervisedEngine,
            EnrollmentEngine enrollmentEngine,
            HybridEngine hybridEngine) {
        this.unsupervisedEngine = unsupervisedEngine;
        this.enrollmentEngine = enrollmentEngine;
        this.hybridEngine = hybridEngine;
    }

    @Override
    public SpeakerDiarizationEngine resolve(String mode) {
        return switch (mode) {
            case "enrollment" -> enrollmentEngine;
            case "hybrid" -> hybridEngine;
            default -> unsupervisedEngine;
        };
    }
}
