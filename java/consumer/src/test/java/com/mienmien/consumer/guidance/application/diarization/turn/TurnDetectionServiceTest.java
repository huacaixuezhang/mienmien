package com.mienmien.consumer.guidance.application.diarization.turn;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnDetectionServiceTest {
    @Test
    void constructor_shouldAcceptPublisher() {
        TurnDetectionService service = new TurnDetectionService(event -> {
        });
        assertNotNull(service);
    }

    @Test
    void onFrame_shouldPublishTurnEvents() {
        List<TurnEvent> events = new ArrayList<>();
        TurnDetectionService service = new TurnDetectionService(events::add);
        service.onTranscriptionFrame("gs_test", "speaker_0", "请介绍一下你自己。", false, 0.9d);
        service.onTranscriptionFrame("gs_test", "speaker_1", "好的，我先介绍背景。", false, 0.9d);
        assertTrue(events.size() >= 2);
    }
}
