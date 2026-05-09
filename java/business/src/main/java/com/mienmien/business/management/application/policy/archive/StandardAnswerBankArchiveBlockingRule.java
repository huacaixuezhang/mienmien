package com.mienmien.business.management.application.policy.archive;

import com.mienmien.business.management.domain.repository.StandardAnswerBankRepository;
import org.springframework.stereotype.Component;

@Component
public class StandardAnswerBankArchiveBlockingRule implements ArchiveBlockingRule {
    private final StandardAnswerBankRepository standardAnswerBankRepository;

    public StandardAnswerBankArchiveBlockingRule(StandardAnswerBankRepository standardAnswerBankRepository) {
        this.standardAnswerBankRepository = standardAnswerBankRepository;
    }

    @Override
    public long countBlocking(String spaceId) {
        return standardAnswerBankRepository.countBySpaceId(spaceId);
    }

    @Override
    public String description() {
        return "标准题库";
    }
}
