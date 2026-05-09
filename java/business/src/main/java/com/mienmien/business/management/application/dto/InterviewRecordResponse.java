package com.mienmien.business.management.application.dto;

import com.mienmien.business.management.domain.model.InterviewRecord;

public record InterviewRecordResponse(
        String recordId,
        String spaceId,
        String type,
        int round,
        String interviewType,
        int score,
        String result,
        String summary
) {
    public static InterviewRecordResponse from(InterviewRecord r) {
        return new InterviewRecordResponse(
                r.getRecordId(),
                r.getSpaceId(),
                r.getCategory(),
                r.getRound(),
                r.getInterviewType(),
                r.getScore(),
                r.getResult(),
                r.getSummary()
        );
    }
}
