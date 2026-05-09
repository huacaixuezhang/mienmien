package com.mienmien.consumer.guidance.domain.model;

import com.mienmien.consumer.guidance.domain.DomainException;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public final class GuidanceSession {
    private static final Set<String> MODES = Set.of("mock", "live", "photo");

    private final String sessionId;
    private final String userId;
    private final String mode;
    private final String status;
    private final Instant startedAt;
    /** 用户主动结束会话的时间；为 null 表示未正式结束。 */
    private final Instant endedAt;

    private GuidanceSession(String sessionId, String userId, String mode, String status, Instant startedAt,
                            Instant endedAt) {
        this.sessionId = Objects.requireNonNull(sessionId);
        this.userId = Objects.requireNonNull(userId);
        this.mode = Objects.requireNonNull(mode);
        this.status = Objects.requireNonNull(status);
        this.startedAt = Objects.requireNonNull(startedAt);
        this.endedAt = endedAt;
    }

    /**
     * 自持久化层还原（读库）。
     */
    public static GuidanceSession restore(
            String sessionId,
            String userId,
            String mode,
            String status,
            Instant startedAt,
            Instant endedAt) {
        return new GuidanceSession(sessionId, userId, mode, status, startedAt, endedAt);
    }

    public static GuidanceSession createNew(String sessionId, String userId, String mode) {
        String m = mode == null || mode.isBlank() ? "live" : mode;
        if (!MODES.contains(m)) {
            throw new DomainException("CON-4001", "会话模式仅支持 mock、live、photo");
        }
        String uid = userId == null || userId.isBlank() ? "user_001" : userId;
        return new GuidanceSession(sessionId, uid, m, "init", Instant.now(), null);
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getMode() {
        return mode;
    }

    public String getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public boolean isClosed() {
        return endedAt != null;
    }
}
