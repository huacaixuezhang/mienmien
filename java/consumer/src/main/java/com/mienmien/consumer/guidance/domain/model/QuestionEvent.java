package com.mienmien.consumer.guidance.domain.model;

import com.mienmien.consumer.guidance.domain.DomainException;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public final class QuestionEvent {
    private static final Set<String> SOURCES = Set.of("voice", "photo", "text");

    private final String eventId;
    private final String sessionId;
    private final String source;
    private final String questionText;
    private final Instant createdAt;

    private QuestionEvent(String eventId, String sessionId, String source, String questionText, Instant createdAt) {
        this.eventId = Objects.requireNonNull(eventId);
        this.sessionId = Objects.requireNonNull(sessionId);
        this.source = Objects.requireNonNull(source);
        this.questionText = Objects.requireNonNull(questionText);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static QuestionEvent create(String eventId, String sessionId, String source, String questionText) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new DomainException("CON-4001", "sessionId 不能为空");
        }
        if (!SOURCES.contains(source)) {
            throw new DomainException("CON-4001", "问题来源仅支持 voice、photo、text");
        }
        String text = questionText == null ? "" : questionText;
        if ("text".equals(source) && text.isBlank()) {
            throw new DomainException("CON-4001", "文本问题不能为空");
        }
        return new QuestionEvent(eventId, sessionId, source, text, Instant.now());
    }

    public String getEventId() {
        return eventId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getSource() {
        return source;
    }

    public String getQuestionText() {
        return questionText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
