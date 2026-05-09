package com.mienmien.consumer.guidance.domain.repository;

public interface AnswerStreamRepository {
    void saveWithLatestEvent(String streamId, String sessionId, String finalAnswer);
}
