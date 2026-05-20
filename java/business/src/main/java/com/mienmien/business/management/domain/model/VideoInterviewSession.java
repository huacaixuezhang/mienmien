package com.mienmien.business.management.domain.model;

import com.mienmien.business.management.domain.DomainException;

import java.time.Instant;
import java.util.Objects;

/**
 * 视频模拟面试运行时会话头：快照在创建时固化，供 consumer 推理使用。
 */
public final class VideoInterviewSession {
    private final String sessionId;
    private final String userId;
    private final String spaceId;
    private final String businessRecordId;
    private final String positionIdOrNull;
    private final int roundIndex;
    private final String styleKey;
    private final String status;
    private final long epoch;
    private final String resumeSnapshotJson;
    private final String jobSnapshotJson;
    private final String stylePromptSnapshot;
    private final String orchestratorModel;
    private final String asrModel;
    private final Instant startedAt;
    private final Instant endedAtOrNull;

    private VideoInterviewSession(
            String sessionId,
            String userId,
            String spaceId,
            String businessRecordId,
            String positionIdOrNull,
            int roundIndex,
            String styleKey,
            String status,
            long epoch,
            String resumeSnapshotJson,
            String jobSnapshotJson,
            String stylePromptSnapshot,
            String orchestratorModel,
            String asrModel,
            Instant startedAt,
            Instant endedAtOrNull) {
        this.sessionId = Objects.requireNonNull(sessionId);
        this.userId = Objects.requireNonNull(userId);
        this.spaceId = Objects.requireNonNull(spaceId);
        this.businessRecordId = Objects.requireNonNull(businessRecordId);
        this.positionIdOrNull = positionIdOrNull;
        this.roundIndex = roundIndex;
        this.styleKey = styleKey == null ? "" : styleKey;
        this.status = status == null ? "preparing" : status;
        this.epoch = epoch;
        this.resumeSnapshotJson = resumeSnapshotJson == null ? "{}" : resumeSnapshotJson;
        this.jobSnapshotJson = jobSnapshotJson == null ? "{}" : jobSnapshotJson;
        this.stylePromptSnapshot = stylePromptSnapshot == null ? "" : stylePromptSnapshot;
        this.orchestratorModel = orchestratorModel == null ? "" : orchestratorModel;
        this.asrModel = asrModel == null ? "" : asrModel;
        this.startedAt = startedAt != null ? startedAt : Instant.now();
        this.endedAtOrNull = endedAtOrNull;
    }

    public static VideoInterviewSession createNew(
            String sessionId,
            String userId,
            String spaceId,
            String businessRecordId,
            String positionIdOrNull,
            int roundIndex,
            String styleKey,
            String resumeSnapshotJson,
            String jobSnapshotJson,
            String stylePromptSnapshot,
            String orchestratorModel,
            String asrModel) {
        if (sessionId.isBlank() || userId.isBlank() || spaceId.isBlank() || businessRecordId.isBlank()) {
            throw new DomainException("BUS-4001", "会话标识、用户、空间与面试记录不能为空");
        }
        return new VideoInterviewSession(
                sessionId,
                userId,
                spaceId,
                businessRecordId,
                positionIdOrNull,
                roundIndex,
                styleKey,
                "preparing",
                0L,
                resumeSnapshotJson,
                jobSnapshotJson,
                stylePromptSnapshot,
                orchestratorModel,
                asrModel,
                Instant.now(),
                null);
    }

    /** 自持久化行还原（基础设施层使用）。 */
    public static VideoInterviewSession restoreRow(
            String sessionId,
            String userId,
            String spaceId,
            String businessRecordId,
            String positionIdOrNull,
            int roundIndex,
            String styleKey,
            String status,
            long epoch,
            String resumeSnapshotJson,
            String jobSnapshotJson,
            String stylePromptSnapshot,
            String orchestratorModel,
            String asrModel,
            Instant startedAt,
            Instant endedAtOrNull) {
        return new VideoInterviewSession(
                sessionId,
                userId,
                spaceId,
                businessRecordId,
                positionIdOrNull,
                roundIndex,
                styleKey,
                status,
                epoch,
                resumeSnapshotJson,
                jobSnapshotJson,
                stylePromptSnapshot,
                orchestratorModel,
                asrModel,
                startedAt,
                endedAtOrNull);
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getSpaceId() {
        return spaceId;
    }

    public String getBusinessRecordId() {
        return businessRecordId;
    }

    public String getPositionIdOrNull() {
        return positionIdOrNull;
    }

    public int getRoundIndex() {
        return roundIndex;
    }

    public String getStyleKey() {
        return styleKey;
    }

    public String getStatus() {
        return status;
    }

    public long getEpoch() {
        return epoch;
    }

    public String getResumeSnapshotJson() {
        return resumeSnapshotJson;
    }

    public String getJobSnapshotJson() {
        return jobSnapshotJson;
    }

    public String getStylePromptSnapshot() {
        return stylePromptSnapshot;
    }

    public String getOrchestratorModel() {
        return orchestratorModel;
    }

    public String getAsrModel() {
        return asrModel;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAtOrNull() {
        return endedAtOrNull;
    }
}
