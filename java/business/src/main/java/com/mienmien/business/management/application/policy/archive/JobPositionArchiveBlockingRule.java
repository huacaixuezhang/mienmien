package com.mienmien.business.management.application.policy.archive;

import com.mienmien.business.management.domain.repository.JobPositionRepository;
import org.springframework.stereotype.Component;

@Component
public class JobPositionArchiveBlockingRule implements ArchiveBlockingRule {
    private final JobPositionRepository jobPositionRepository;

    public JobPositionArchiveBlockingRule(JobPositionRepository jobPositionRepository) {
        this.jobPositionRepository = jobPositionRepository;
    }

    @Override
    public long countBlocking(String spaceId) {
        return jobPositionRepository.countBySpaceId(spaceId);
    }

    @Override
    public String description() {
        return "在招岗位";
    }
}
