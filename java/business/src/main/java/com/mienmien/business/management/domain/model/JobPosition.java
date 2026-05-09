package com.mienmien.business.management.domain.model;

import com.mienmien.business.management.domain.DomainException;

import java.time.Instant;
import java.util.Objects;

/**
 * 岗位实体（首期由 JdTarget 之外的独立表承载，便于扩展编制与状态）。
 */
public final class JobPosition {
    private final String positionId;
    private final String spaceId;
    private String title;
    private String company;
    private String location;
    private String baseRange;
    private String status;
    private final Instant createdAt;
    private Instant updatedAt;

    private JobPosition(
            String positionId,
            String spaceId,
            String title,
            String company,
            String location,
            String baseRange,
            String status,
            Instant createdAt,
            Instant updatedAt) {
        this.positionId = Objects.requireNonNull(positionId);
        this.spaceId = Objects.requireNonNull(spaceId);
        this.title = Objects.requireNonNull(title);
        this.company = Objects.requireNonNull(company);
        this.location = Objects.requireNonNull(location);
        this.baseRange = Objects.requireNonNull(baseRange);
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static JobPosition createNew(
            String positionId,
            String spaceId,
            String title,
            String company,
            String location,
            String baseRange) {
        if (spaceId == null || spaceId.isBlank()) {
            throw new DomainException("BUS-4001", "spaceId 不能为空");
        }
        if (title == null || title.isBlank()) {
            throw new DomainException("BUS-4001", "岗位标题不能为空");
        }
        Instant now = Instant.now();
        return new JobPosition(
                positionId,
                spaceId,
                title.trim(),
                company == null ? "" : company.trim(),
                location == null ? "" : location.trim(),
                baseRange == null ? "" : baseRange.trim(),
                "ACTIVE",
                now,
                now
        );
    }

    public static JobPosition restore(
            String positionId,
            String spaceId,
            String title,
            String company,
            String location,
            String baseRange,
            String status,
            Instant createdAt,
            Instant updatedAt) {
        return new JobPosition(positionId, spaceId, title, company, location, baseRange, status, createdAt, updatedAt);
    }

    public void updateProfile(String title, String company, String location, String baseRange) {
        if ("CLOSED".equals(this.status)) {
            throw new DomainException("BUS-4001", "已关闭岗位不可修改");
        }
        if (title == null || title.isBlank()) {
            throw new DomainException("BUS-4001", "岗位标题不能为空");
        }
        this.title = title.trim();
        this.company = company == null ? "" : company.trim();
        this.location = location == null ? "" : location.trim();
        this.baseRange = baseRange == null ? "" : baseRange.trim();
        this.updatedAt = Instant.now();
    }

    public void markClosed() {
        if ("CLOSED".equals(this.status)) {
            return;
        }
        this.status = "CLOSED";
        this.updatedAt = Instant.now();
    }

    public String getPositionId() {
        return positionId;
    }

    public String getSpaceId() {
        return spaceId;
    }

    public String getTitle() {
        return title;
    }

    public String getCompany() {
        return company;
    }

    public String getLocation() {
        return location;
    }

    public String getBaseRange() {
        return baseRange;
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
}
