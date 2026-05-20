package com.mienmien.business.management.domain.model;

import com.mienmien.business.management.domain.DomainException;

import java.time.Instant;
import java.util.Objects;

/** 用户自定义的 AI 面试官风格（Prompt 正文由用户维护）。 */
public final class UserInterviewerStyle {
    private final String styleId;
    private final String userId;
    private String title;
    private String promptBody;
    private final Instant createdAt;
    private Instant updatedAt;

    private UserInterviewerStyle(
            String styleId,
            String userId,
            String title,
            String promptBody,
            Instant createdAt,
            Instant updatedAt) {
        this.styleId = Objects.requireNonNull(styleId);
        this.userId = Objects.requireNonNull(userId);
        this.title = Objects.requireNonNull(title);
        this.promptBody = Objects.requireNonNull(promptBody);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static UserInterviewerStyle createNew(String styleId, String userId, String title, String promptBody) {
        if (userId == null || userId.isBlank()) {
            throw new DomainException("BUS-4001", "userId 不能为空");
        }
        if (title == null || title.isBlank()) {
            throw new DomainException("BUS-4001", "风格名称不能为空");
        }
        if (promptBody == null || promptBody.isBlank()) {
            throw new DomainException("BUS-4001", "Prompt 内容不能为空");
        }
        Instant now = Instant.now();
        return new UserInterviewerStyle(styleId, userId, title.trim(), promptBody, now, now);
    }

    public static UserInterviewerStyle restore(
            String styleId, String userId, String title, String promptBody, Instant createdAt, Instant updatedAt) {
        return new UserInterviewerStyle(styleId, userId, title, promptBody, createdAt, updatedAt);
    }

    public void updateProfile(String title, String promptBody) {
        if (title == null || title.isBlank()) {
            throw new DomainException("BUS-4001", "风格名称不能为空");
        }
        if (promptBody == null || promptBody.isBlank()) {
            throw new DomainException("BUS-4001", "Prompt 内容不能为空");
        }
        this.title = title.trim();
        this.promptBody = promptBody;
        this.updatedAt = Instant.now();
    }

    public String getStyleId() {
        return styleId;
    }

    public String getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public String getPromptBody() {
        return promptBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
