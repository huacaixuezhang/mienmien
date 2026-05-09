package com.mienmien.consumer.guidance.domain.model;

import com.mienmien.consumer.guidance.domain.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GuidanceSessionTest {
    @Test
    void createNew_rejectsInvalidMode() {
        assertThrows(DomainException.class, () -> GuidanceSession.createNew("gs_x", "u", "bad"));
    }

    @Test
    void createNew_defaultsLive() {
        GuidanceSession s = GuidanceSession.createNew("gs_x", "u", null);
        assertEquals("live", s.getMode());
    }

    @Test
    void restore_preservesEndedAt() {
        var started = java.time.Instant.parse("2026-04-10T00:00:00Z");
        var ended = java.time.Instant.parse("2026-04-10T01:00:00Z");
        GuidanceSession s = GuidanceSession.restore("gs_1", "u", "live", "completed", started, ended);
        assertTrue(s.isClosed());
    }
}
