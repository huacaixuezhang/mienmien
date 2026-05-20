package com.mienmien.business.management.application.dto;

import java.time.Instant;

public record VideoInterviewSessionCreatedResponse(
        String sessionId,
        String businessRecordId,
        String spaceId,
        /** 本场固化的面试官风格 key（内置 builtin_* 或自定义 styleId），与 stylePrompt 快照一致 */
        String interviewerStyleKey,
        String consumerHttpBaseUrl,
        /** 不含 host，形如 {@code /ws/consumer/video-interview/{sessionId}}，由前端拼接 ws(s):// */
        String videoInterviewWebSocketPath,
        String status,
        String orchestratorModel,
        String asrModel,
        Instant startedAt) {
}
