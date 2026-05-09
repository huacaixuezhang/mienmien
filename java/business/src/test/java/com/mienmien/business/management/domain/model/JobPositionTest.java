package com.mienmien.business.management.domain.model;

import com.mienmien.business.management.domain.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JobPositionTest {
    @Test
    void createNew_rejectsBlankTitle() {
        assertThrows(DomainException.class, () -> JobPosition.createNew("jp_1", "sp_1", "  ", "c", "l", "r"));
    }

    @Test
    void markClosed_idempotent() {
        JobPosition p = JobPosition.createNew("jp_1", "sp_1", "T", "", "", "");
        p.markClosed();
        p.markClosed();
        assertEquals("CLOSED", p.getStatus());
    }
}
