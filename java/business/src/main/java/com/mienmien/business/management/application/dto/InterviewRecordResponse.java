package com.mienmien.business.management.application.dto;

import java.time.Instant;

public record InterviewRecordResponse(
        String recordId,
        String spaceId,
        String type,
        int round,
        String interviewType,
        int score,
        String result,
        String summary,
        /** 绑定岗位 ID，无绑定时为 null */
        String positionId,
        Instant createdAt
) {
}
