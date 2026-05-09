package com.mienmien.consumer.guidance.domain.repository;

import com.mienmien.consumer.guidance.domain.model.QuestionEvent;

import java.util.Optional;

public interface QuestionEventRepository {
    void save(QuestionEvent event);

    Optional<String> findLatestQuestionText(String sessionId);

    Optional<String> findLatestQuestionEventId(String sessionId);
}
