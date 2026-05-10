package com.mienmien.business.management.application.service;

import com.mienmien.business.management.application.dto.InterviewRecordResponse;
import com.mienmien.business.management.application.support.ApplicationSpaceGuard;
import com.mienmien.business.management.domain.model.InterviewRecord;
import com.mienmien.business.management.domain.repository.SpaceRepository;
import com.mienmien.business.management.domain.support.ShortIdGenerator;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class InterviewApplicationService {
    private final SpaceRepository spaceRepository;
    private final ShortIdGenerator shortIdGenerator;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<StoredInterview>> bySpace = new ConcurrentHashMap<>();

    public InterviewApplicationService(SpaceRepository spaceRepository, ShortIdGenerator shortIdGenerator) {
        this.spaceRepository = spaceRepository;
        this.shortIdGenerator = shortIdGenerator;
    }

    public InterviewRecordResponse create(
            String category,
            String spaceId,
            String interviewType,
            int round,
            int score,
            String result,
            String summary) {
        ApplicationSpaceGuard.requireOwnedActiveSpace(spaceId, spaceRepository);
        String recordId = shortIdGenerator.newId("i");
        InterviewRecord record = InterviewRecord.create(recordId, spaceId, category, round, interviewType, score, result, summary);
        StoredInterview stored = new StoredInterview(record, Instant.now());
        bySpace.computeIfAbsent(spaceId, k -> new CopyOnWriteArrayList<>()).add(stored);
        return toResponse(stored);
    }

    public List<InterviewRecordResponse> listBySpace(String spaceId) {
        ApplicationSpaceGuard.requireOwnedActiveSpace(spaceId, spaceRepository);
        CopyOnWriteArrayList<StoredInterview> list = bySpace.get(spaceId);
        if (list == null) {
            return List.of();
        }
        return list.stream().map(InterviewApplicationService::toResponse).toList();
    }

    private static InterviewRecordResponse toResponse(StoredInterview s) {
        InterviewRecord r = s.record();
        return new InterviewRecordResponse(
                r.getRecordId(),
                r.getSpaceId(),
                r.getCategory(),
                r.getRound(),
                r.getInterviewType(),
                r.getScore(),
                r.getResult(),
                r.getSummary(),
                s.createdAt()
        );
    }

    private record StoredInterview(InterviewRecord record, Instant createdAt) {
    }
}
