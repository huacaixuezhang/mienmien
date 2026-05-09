package com.mienmien.business.management.application.policy.archive;

import com.mienmien.business.management.domain.repository.JdTargetRepository;
import org.springframework.stereotype.Component;

@Component
public class JdArchiveBlockingRule implements ArchiveBlockingRule {
    private final JdTargetRepository jdTargetRepository;

    public JdArchiveBlockingRule(JdTargetRepository jdTargetRepository) {
        this.jdTargetRepository = jdTargetRepository;
    }

    @Override
    public long countBlocking(String spaceId) {
        return jdTargetRepository.countBySpaceId(spaceId);
    }

    @Override
    public String description() {
        return "JD";
    }
}
