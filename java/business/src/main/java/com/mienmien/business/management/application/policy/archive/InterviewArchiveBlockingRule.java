package com.mienmien.business.management.application.policy.archive;

import com.mienmien.business.management.domain.repository.InterviewRecordRepository;
import org.springframework.stereotype.Component;

@Component
public class InterviewArchiveBlockingRule implements ArchiveBlockingRule {
    private final InterviewRecordRepository interviewRecordRepository;

    public InterviewArchiveBlockingRule(InterviewRecordRepository interviewRecordRepository) {
        this.interviewRecordRepository = interviewRecordRepository;
    }

    @Override
    public long countBlocking(String spaceId) {
        return interviewRecordRepository.countBySpaceId(spaceId);
    }

    @Override
    public String description() {
        return "面试";
    }
}
