package com.mienmien.business.management.application.policy.archive;

import com.mienmien.business.management.domain.repository.ResumeRepository;
import org.springframework.stereotype.Component;

@Component
public class ResumeArchiveBlockingRule implements ArchiveBlockingRule {
    private final ResumeRepository resumeRepository;

    public ResumeArchiveBlockingRule(ResumeRepository resumeRepository) {
        this.resumeRepository = resumeRepository;
    }

    @Override
    public long countBlocking(String spaceId) {
        return resumeRepository.countBySpaceId(spaceId);
    }

    @Override
    public String description() {
        return "简历";
    }
}
