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
        Instant createdAt
) {
}
