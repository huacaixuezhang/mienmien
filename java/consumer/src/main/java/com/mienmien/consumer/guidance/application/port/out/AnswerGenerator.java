package com.mienmien.consumer.guidance.application.port.out;

import java.util.List;

public interface AnswerGenerator {
    String photoQaAdvisory();

    String onceAnswer();

    List<String> progressiveChunks();

    String composeFinalAnswer(String question);

    String streamFallbackMessage();
}
