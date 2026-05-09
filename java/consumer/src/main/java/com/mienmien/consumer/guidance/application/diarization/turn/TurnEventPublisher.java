package com.mienmien.consumer.guidance.application.diarization.turn;

public interface TurnEventPublisher {
    void publish(TurnEvent event);
}
