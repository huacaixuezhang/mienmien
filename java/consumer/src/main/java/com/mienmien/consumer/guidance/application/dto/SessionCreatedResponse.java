package com.mienmien.consumer.guidance.application.dto;

import com.mienmien.consumer.guidance.domain.model.GuidanceSession;

public record SessionCreatedResponse(
        String sessionId,
        String userId,
        String mode,
        String status,
        String startedAt
) {
    public static SessionCreatedResponse from(GuidanceSession s) {
        return new SessionCreatedResponse(
                s.getSessionId(),
                s.getUserId(),
                s.getMode(),
                s.getStatus(),
                s.getStartedAt().toString()
        );
    }
}
