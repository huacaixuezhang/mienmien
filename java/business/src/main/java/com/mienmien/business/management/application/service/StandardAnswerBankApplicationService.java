package com.mienmien.business.management.application.service;

import com.mienmien.business.management.application.dto.StandardAnswerBankResponse;
import com.mienmien.business.management.application.support.ApplicationSpaceGuard;
import com.mienmien.business.management.domain.model.StandardAnswerBank;
import com.mienmien.business.management.domain.repository.SpaceRepository;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class StandardAnswerBankApplicationService {
    private final SpaceRepository spaceRepository;
    private final ConcurrentHashMap<String, StandardAnswerBank> banksBySpace = new ConcurrentHashMap<>();

    public StandardAnswerBankApplicationService(SpaceRepository spaceRepository) {
        this.spaceRepository = spaceRepository;
    }

    public StandardAnswerBankResponse getBySpace(String spaceId) {
        ApplicationSpaceGuard.requireOwnedActiveSpace(spaceId, spaceRepository);
        StandardAnswerBank bank = banksBySpace.get(spaceId);
        if (bank == null) {
            String answerId = "ab_" + spaceId;
            bank = StandardAnswerBank.createNew(answerId, spaceId, "", "", "", "", "", "");
            banksBySpace.put(spaceId, bank);
        }
        return toResponse(bank);
    }

    public StandardAnswerBankResponse upsert(
            String spaceId,
            String intro,
            String reason,
            String strengths,
            String project,
            String hr,
            String cardsJson) {
        ApplicationSpaceGuard.requireOwnedActiveSpace(spaceId, spaceRepository);
        StandardAnswerBank existing = banksBySpace.get(spaceId);
        if (existing == null) {
            String answerId = "ab_" + spaceId;
            StandardAnswerBank created = StandardAnswerBank.createNew(
                    answerId, spaceId, intro, reason, strengths, project, hr, cardsJson);
            banksBySpace.put(spaceId, created);
            return toResponse(created);
        }
        existing.update(intro, reason, strengths, project, hr, cardsJson);
        return toResponse(existing);
    }

    private static StandardAnswerBankResponse toResponse(StandardAnswerBank b) {
        return new StandardAnswerBankResponse(
                b.getAnswerId(),
                b.getSpaceId(),
                b.getIntro(),
                b.getReason(),
                b.getStrengths(),
                b.getProject(),
                b.getHr(),
                b.getCardsJson(),
                b.getUpdatedAt()
        );
    }
}
