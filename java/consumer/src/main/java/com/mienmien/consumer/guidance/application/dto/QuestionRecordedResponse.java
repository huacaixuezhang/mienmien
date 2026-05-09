package com.mienmien.consumer.guidance.application.dto;

import com.mienmien.consumer.guidance.domain.model.QuestionEvent;

public record QuestionRecordedResponse(
        String eventId,
        String sessionId,
        String source,
        String questionText
) {
    public static QuestionRecordedResponse from(QuestionEvent e) {
        return new QuestionRecordedResponse(
                e.getEventId(),
                e.getSessionId(),
                e.getSource(),
                e.getQuestionText()
        );
    }
}
