package com.mienmien.business.management.domain.model;

import com.mienmien.business.management.domain.DomainException;

import java.time.Instant;
import java.util.Objects;

/**
 * 聚合根：求职管理空间（多空间隔离）。
 */
public final class Space {
    private final String spaceId;
    private final String ownerUserId;
    private String name;
    private String status;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    private Space(String spaceId, String ownerUserId, String name, String status, Instant createdAt, Instant updatedAt, Instant deletedAt) {
        this.spaceId = Objects.requireNonNull(spaceId);
        this.ownerUserId = Objects.requireNonNull(ownerUserId);
        this.name = Objects.requireNonNull(name);
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.deletedAt = deletedAt;
    }

    public static Space createNew(String spaceId, String ownerUserId, String name) {
        if (name == null || name.isBlank()) {
            throw new DomainException("BUS-4001", "空间名称不能为空");
        }
        Instant now = Instant.now();
        return new Space(spaceId, ownerUserId, name.trim(), "ACTIVE", now, now, null);
    }

    public static Space restore(String spaceId, String ownerUserId, String name, String status,
                                Instant createdAt, Instant updatedAt, Instant deletedAt) {
        return new Space(spaceId, ownerUserId, name, status, createdAt, updatedAt, deletedAt);
    }

    public void rename(String newName) {
        if ("ARCHIVED".equals(this.status)) {
            throw new DomainException("BUS-4001", "已归档空间不可修改名称");
        }
        if (newName == null || newName.isBlank()) {
            throw new DomainException("BUS-4001", "空间名称不能为空");
        }
        this.name = newName.trim();
        this.updatedAt = Instant.now();
    }

    public void markArchived() {
        if ("ARCHIVED".equals(this.status)) {
            return;
        }
        this.status = "ARCHIVED";
        this.updatedAt = Instant.now();
        this.deletedAt = null;
    }

    public void moveToRecycleBin() {
        if ("RECYCLED".equals(this.status)) {
            return;
        }
        this.status = "RECYCLED";
        this.deletedAt = Instant.now();
        this.updatedAt = this.deletedAt;
    }

    public void restoreFromRecycleBin() {
        if (!"RECYCLED".equals(this.status)) {
            return;
        }
        this.status = "ACTIVE";
        this.deletedAt = null;
        this.updatedAt = Instant.now();
    }

    public boolean isArchived() {
        return "ARCHIVED".equals(this.status);
    }

    public boolean isRecycled() {
        return "RECYCLED".equals(this.status);
    }

    public String getSpaceId() {
        return spaceId;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
