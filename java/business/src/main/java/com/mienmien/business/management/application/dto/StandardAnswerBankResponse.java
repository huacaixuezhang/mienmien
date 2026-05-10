package com.mienmien.business.management.application.dto;

import java.time.Instant;

public record StandardAnswerBankResponse(
        String answerId,
        String spaceId,
        String intro,
        String reason,
        String strengths,
        String project,
        String hr,
        String cardsJson,
        Instant updatedAt
) {
}
