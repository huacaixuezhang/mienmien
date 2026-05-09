package com.mienmien.business.management.application.dto;

import com.mienmien.business.management.domain.model.StandardAnswerBank;

public record StandardAnswerBankResponse(
        String answerId,
        String spaceId,
        String intro,
        String reason,
        String strengths,
        String project,
        String hr,
        String cardsJson,
        String updatedAt
) {
    public static StandardAnswerBankResponse from(StandardAnswerBank bank) {
        return new StandardAnswerBankResponse(
                bank.getAnswerId(),
                bank.getSpaceId(),
                bank.getIntro(),
                bank.getReason(),
                bank.getStrengths(),
                bank.getProject(),
                bank.getHr(),
                bank.getCardsJson(),
                bank.getUpdatedAt().toString()
        );
    }
}
