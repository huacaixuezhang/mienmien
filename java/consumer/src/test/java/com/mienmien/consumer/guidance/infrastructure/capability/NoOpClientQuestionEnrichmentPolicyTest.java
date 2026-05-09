package com.mienmien.consumer.guidance.infrastructure.capability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NoOpClientQuestionEnrichmentPolicyTest {
    @Test
    void trimsInput() {
        var p = new NoOpClientQuestionEnrichmentPolicy();
        assertEquals("a", p.enrichVoiceQuestion("s", "  a  "));
        assertEquals("b", p.enrichPhotoQuestion("s", "b\n"));
    }
}
