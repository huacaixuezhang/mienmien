package com.mienmien.business.management.domain.model;

import com.mienmien.business.management.domain.DomainException;

import java.time.Instant;
import java.util.Objects;

public final class Resume {
    private final String resumeId;
    private final String spaceId;
    private final int version;
    private final String content;
    private final boolean active;
    private final Instant updatedAt;

    private Resume(String resumeId, String spaceId, int version, String content, boolean active, Instant updatedAt) {
        this.resumeId = Objects.requireNonNull(resumeId);
        this.spaceId = Objects.requireNonNull(spaceId);
        this.version = version;
        this.content = Objects.requireNonNull(content);
        this.active = active;
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static Resume createNew(String resumeId, String spaceId, int version, String content) {
        if (spaceId == null || spaceId.isBlank()) {
            throw new DomainException("BUS-4001", "spaceId 不能为空");
        }
        if (version < 1) {
            throw new DomainException("BUS-4001", "简历版本号必须 >= 1");
        }
        return new Resume(resumeId, spaceId, version, content == null ? "" : content, true, Instant.now());
    }

    public static Resume restore(String resumeId, String spaceId, int version, String content,
                                 boolean active, Instant updatedAt) {
        return new Resume(resumeId, spaceId, version, content, active, updatedAt);
    }

    public String getResumeId() {
        return resumeId;
    }

    public String getSpaceId() {
        return spaceId;
    }

    public int getVersion() {
        return version;
    }

    public String getContent() {
        return content;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
