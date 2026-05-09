package com.mienmien.business.management.application.service;

import com.mienmien.business.management.application.dto.StandardAnswerBankResponse;
import com.mienmien.business.management.application.policy.SpaceAccessPolicy;
import com.mienmien.business.management.application.security.BusinessRequestActor;
import com.mienmien.business.management.domain.model.StandardAnswerBank;
import com.mienmien.business.management.domain.repository.StandardAnswerBankRepository;
import com.mienmien.business.management.domain.support.ShortIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class StandardAnswerBankApplicationService {
    private final StandardAnswerBankRepository standardAnswerBankRepository;
    private final SpaceAccessPolicy spaceAccessPolicy;
    private final ShortIdGenerator shortIdGenerator;

    public StandardAnswerBankApplicationService(
            StandardAnswerBankRepository standardAnswerBankRepository,
            SpaceAccessPolicy spaceAccessPolicy,
            ShortIdGenerator shortIdGenerator) {
        this.standardAnswerBankRepository = standardAnswerBankRepository;
        this.spaceAccessPolicy = spaceAccessPolicy;
        this.shortIdGenerator = shortIdGenerator;
    }

    @Transactional(readOnly = true)
    public StandardAnswerBankResponse getBySpace(String spaceId) {
        spaceAccessPolicy.requireReadableSpaceForActor(spaceId, BusinessRequestActor.requireUserId());
        return standardAnswerBankRepository.findBySpaceId(spaceId)
                .map(StandardAnswerBankResponse::from)
                .orElseGet(() -> StandardAnswerBankResponse.from(StandardAnswerBank.createNew(
                        shortIdGenerator.newId("ab_"),
                        spaceId,
                        "",
                        "",
                        "",
                        "",
                        "",
                        ""
                )));
    }

    @Transactional
    public StandardAnswerBankResponse upsert(
            String spaceId,
            String intro,
            String reason,
            String strengths,
            String project,
            String hr,
            String cardsJson) {
        spaceAccessPolicy.requireWritableSpaceForActor(spaceId, BusinessRequestActor.requireUserId());
        Optional<StandardAnswerBank> existing = standardAnswerBankRepository.findBySpaceId(spaceId);
        StandardAnswerBank bank = existing.orElseGet(() -> StandardAnswerBank.createNew(
                        shortIdGenerator.newId("ab_"),
                        spaceId,
                        intro,
                        reason,
                        strengths,
                        project,
                        hr,
                        cardsJson
                ));
        if (existing.isPresent()) {
            bank.update(intro, reason, strengths, project, hr, cardsJson);
            standardAnswerBankRepository.update(bank);
        } else {
            standardAnswerBankRepository.save(bank);
        }
        return StandardAnswerBankResponse.from(bank);
    }
}
