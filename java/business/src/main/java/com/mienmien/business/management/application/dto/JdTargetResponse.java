package com.mienmien.business.management.application.dto;

import java.time.Instant;

public record JdTargetResponse(
        String jdId,
        String spaceId,
        String sourceType,
        String rawText,
        String focusPoints,
        Instant createdAt
) {
}
