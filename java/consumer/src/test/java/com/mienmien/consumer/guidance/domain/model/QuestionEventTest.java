package com.mienmien.consumer.guidance.domain.model;

import com.mienmien.consumer.guidance.domain.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuestionEventTest {
    @Test
    void textSource_rejectsBlank() {
        assertThrows(DomainException.class, () -> QuestionEvent.create("qe_1", "gs_1", "text", "   "));
    }

    @Test
    void voiceAllowsDefaultEmptyHandledInApp() {
        QuestionEvent e = QuestionEvent.create("qe_1", "gs_1", "voice", "");
        assertEquals("", e.getQuestionText());
    }
}
