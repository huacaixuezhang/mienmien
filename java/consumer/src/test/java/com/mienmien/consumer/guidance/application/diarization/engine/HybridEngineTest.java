package com.mienmien.consumer.guidance.application.diarization.engine;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class HybridEngineTest {
    @Test
    void process_shouldReturnResult() throws Exception {
        UnsupervisedEngine unsupervisedEngine = new UnsupervisedEngine();
        EnrollmentEngine enrollmentEngine = new EnrollmentEngine();
        HybridEngine hybrid = new HybridEngine(unsupervisedEngine, enrollmentEngine);
        hybrid.init(Map.of());
        ProcessingResult r = hybrid.process(new byte[]{1, 2, 3, 4}).get();
        assertNotNull(r);
    }
}
