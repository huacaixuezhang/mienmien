package com.mienmien.business.management.application.service;

import com.mienmien.business.management.application.dto.InterviewRecordResponse;
import com.mienmien.business.management.application.policy.SpaceAccessPolicy;
import com.mienmien.business.management.application.security.BusinessRequestActor;
import com.mienmien.business.management.domain.model.InterviewRecord;
import com.mienmien.business.management.domain.repository.InterviewRecordRepository;
import com.mienmien.business.management.domain.support.ShortIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InterviewApplicationService {
    private final InterviewRecordRepository interviewRecordRepository;
    private final SpaceAccessPolicy spaceAccessPolicy;
    private final ShortIdGenerator idGenerator;

    public InterviewApplicationService(
            InterviewRecordRepository interviewRecordRepository,
            SpaceAccessPolicy spaceAccessPolicy,
            ShortIdGenerator idGenerator) {
        this.interviewRecordRepository = interviewRecordRepository;
        this.spaceAccessPolicy = spaceAccessPolicy;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public InterviewRecordResponse create(String category, String spaceId, String interviewType,
                                          int round, int score, String result, String summary) {
        spaceAccessPolicy.requireWritableSpaceForActor(spaceId, BusinessRequestActor.requireUserId());
        InterviewRecord record = InterviewRecord.create(
                idGenerator.newId("ir_"),
                spaceId,
                category,
                round,
                interviewType,
                score,
                result,
                summary
        );
        interviewRecordRepository.save(record);
        return InterviewRecordResponse.from(record);
    }

    @Transactional(readOnly = true)
    public List<InterviewRecordResponse> listBySpace(String spaceId) {
        spaceAccessPolicy.requireReadableSpaceForActor(spaceId, BusinessRequestActor.requireUserId());
        return interviewRecordRepository.findBySpaceIdOrderByCreatedAtDesc(spaceId).stream()
                .map(InterviewRecordResponse::from)
                .toList();
    }
}
