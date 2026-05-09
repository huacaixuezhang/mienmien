package com.mienmien.business.management.domain.model;

import com.mienmien.business.management.domain.DomainException;

import java.util.Objects;
import java.util.Set;

public final class InterviewRecord {
    private static final Set<String> ALLOWED_CATEGORIES = Set.of("mock", "real");

    private final String recordId;
    private final String spaceId;
    private final String category;
    private final int round;
    private final String interviewType;
    private final int score;
    private final String result;
    private final String summary;

    private InterviewRecord(String recordId, String spaceId, String category, int round,
                            String interviewType, int score, String result, String summary) {
        this.recordId = Objects.requireNonNull(recordId);
        this.spaceId = Objects.requireNonNull(spaceId);
        this.category = Objects.requireNonNull(category);
        this.round = round;
        this.interviewType = Objects.requireNonNull(interviewType);
        this.score = score;
        this.result = Objects.requireNonNull(result);
        this.summary = Objects.requireNonNull(summary);
    }

    public static InterviewRecord create(String recordId, String spaceId, String category, int round,
                                         String interviewType, int score, String result, String summary) {
        if (spaceId == null || spaceId.isBlank()) {
            throw new DomainException("BUS-4001", "spaceId 不能为空");
        }
        if (!ALLOWED_CATEGORIES.contains(category)) {
            throw new DomainException("BUS-4001", "面试类别仅支持 mock 或 real");
        }
        if (round < 1) {
            throw new DomainException("BUS-4001", "轮次必须 >= 1");
        }
        return new InterviewRecord(recordId, spaceId, category, round,
                interviewType == null ? "business" : interviewType,
                score,
                result == null ? "pending" : result,
                summary == null ? "" : summary);
    }

    /** 持久化回读（不重复执行业务校验） */
    public static InterviewRecord restore(String recordId, String spaceId, String category, int round,
                                          String interviewType, int score, String result, String summary) {
        return new InterviewRecord(recordId, spaceId, category, round, interviewType, score, result, summary);
    }

    public String getRecordId() {
        return recordId;
    }

    public String getSpaceId() {
        return spaceId;
    }

    public String getCategory() {
        return category;
    }

    public int getRound() {
        return round;
    }

    public String getInterviewType() {
        return interviewType;
    }

    public int getScore() {
        return score;
    }

    public String getResult() {
        return result;
    }

    public String getSummary() {
        return summary;
    }
}
