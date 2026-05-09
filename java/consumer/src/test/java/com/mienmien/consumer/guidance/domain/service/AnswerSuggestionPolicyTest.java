package com.mienmien.consumer.guidance.domain.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnswerSuggestionPolicyTest {
    @Test
    void progressiveChunks_nonEmpty() {
        assertFalse(AnswerSuggestionPolicy.progressiveChunks().isEmpty());
    }

    @Test
    void composeFinalAnswer_includesQuestion() {
        String q = "自我介绍";
        assertTrue(AnswerSuggestionPolicy.composeFinalAnswer(q).contains(q));
    }

    @Test
    void streamFallbackMessage_nonBlank() {
        assertFalse(AnswerSuggestionPolicy.streamFallbackMessage().isBlank());
    }
}
