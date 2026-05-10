package com.mienmien.business.management.domain.model;

import com.mienmien.business.management.domain.DomainException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 简历聚合：唯一键 + 名称 + 归属用户 + 模块列表；与空间的关联由仓储层维护（多对多）。
 */
public final class ResumeDocument {
    private final String resumeId;
    private final String userId;
    private String name;
    private List<ResumeModule> modules;
    private final Instant createdAt;
    private Instant updatedAt;

    private ResumeDocument(String resumeId, String userId, String name, List<ResumeModule> modules,
                           Instant createdAt, Instant updatedAt) {
        this.resumeId = Objects.requireNonNull(resumeId);
        this.userId = Objects.requireNonNull(userId);
        this.name = Objects.requireNonNull(name);
        this.modules = new ArrayList<>(Objects.requireNonNull(modules));
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static ResumeDocument createNew(String resumeId, String userId, String name, List<ResumeModule> modules) {
        if (userId.isBlank()) {
            throw new DomainException("BUS-4001", "userId 不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new DomainException("BUS-4001", "简历名称不能为空");
        }
        if (modules == null || modules.isEmpty()) {
            throw new DomainException("BUS-4001", "至少包含一个模块");
        }
        Instant now = Instant.now();
        return new ResumeDocument(resumeId, userId, name.trim(), modules, now, now);
    }

    public static ResumeDocument restore(String resumeId, String userId, String name, List<ResumeModule> modules,
                                         Instant createdAt, Instant updatedAt) {
        List<ResumeModule> safe = modules == null || modules.isEmpty()
                ? List.of(new ResumeModule(resumeId + "-m1", "模块1", ""))
                : modules;
        Instant c = createdAt != null ? createdAt : Instant.EPOCH;
        Instant u = updatedAt != null ? updatedAt : Instant.now();
        String safeName = (name == null || name.isBlank()) ? "未命名简历" : name.trim();
        return new ResumeDocument(resumeId, userId, safeName, safe, c, u);
    }

    public void rename(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new DomainException("BUS-4001", "简历名称不能为空");
        }
        this.name = newName.trim();
        this.updatedAt = Instant.now();
    }

    public void replaceModules(List<ResumeModule> next) {
        if (next == null || next.isEmpty()) {
            throw new DomainException("BUS-4001", "至少包含一个模块");
        }
        this.modules = new ArrayList<>(next);
        this.updatedAt = Instant.now();
    }

    public String getResumeId() {
        return resumeId;
    }

    public String getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public List<ResumeModule> getModulesView() {
        return Collections.unmodifiableList(modules);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
