package com.mienmien.business.management.domain.model;

import com.mienmien.business.management.domain.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InterviewRecordTest {
    @Test
    void create_rejectsInvalidCategory() {
        assertThrows(DomainException.class,
                () -> InterviewRecord.create("ir_1", "sp_1", "other", 1, "business", 0, "pending", ""));
    }

    @Test
    void create_rejectsRoundZero() {
        assertThrows(DomainException.class,
                () -> InterviewRecord.create("ir_1", "sp_1", "mock", 0, "business", 0, "pending", ""));
    }

    @Test
    void create_defaults() {
        InterviewRecord r = InterviewRecord.create("ir_1", "sp_1", "real", 1, null, 0, null, null);
        assertEquals("business", r.getInterviewType());
        assertEquals("pending", r.getResult());
        assertEquals("", r.getSummary());
    }
}
