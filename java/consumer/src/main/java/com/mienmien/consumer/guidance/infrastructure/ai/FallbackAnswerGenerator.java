package com.mienmien.consumer.guidance.infrastructure.ai;

import com.mienmien.consumer.guidance.application.port.out.AnswerGenerator;
import com.mienmien.consumer.guidance.domain.service.AnswerSuggestionPolicy;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FallbackAnswerGenerator implements AnswerGenerator {
    @Override
    public String photoQaAdvisory() {
        return AnswerSuggestionPolicy.photoQaAdvisory();
    }

    @Override
    public String onceAnswer() {
        return AnswerSuggestionPolicy.fallbackOnceAnswer();
    }

    @Override
    public List<String> progressiveChunks() {
        return AnswerSuggestionPolicy.progressiveChunks();
    }

    @Override
    public String composeFinalAnswer(String question) {
        return AnswerSuggestionPolicy.composeFinalAnswer(question);
    }

    @Override
    public String streamFallbackMessage() {
        return AnswerSuggestionPolicy.streamFallbackMessage();
    }
}
