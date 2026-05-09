package com.mienmien.business.management.domain.model;

import com.mienmien.business.management.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SpaceTest {
    @Test
    void createNew_rejectsBlankName() {
        assertThrows(DomainException.class, () -> Space.createNew("sp_x", "u", "  "));
    }

    @Test
    void createNew_trimsName() {
        Space s = Space.createNew("sp_x", "u", "  名称  ");
        assertEquals("名称", s.getName());
        assertEquals("ACTIVE", s.getStatus());
    }

    @Test
    void rename_updatesName() {
        Space s = Space.createNew("sp_x", "u", "A");
        s.rename("B");
        assertEquals("B", s.getName());
    }

    @Test
    void rename_rejectsWhenArchived() {
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        Space s = Space.restore("sp_x", "u", "N", "ARCHIVED", t, t, null);
        assertThrows(DomainException.class, () -> s.rename("X"));
    }
}
