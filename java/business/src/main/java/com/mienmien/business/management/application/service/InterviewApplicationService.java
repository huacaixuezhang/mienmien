package com.mienmien.business.management.application.service;

import com.mienmien.business.management.application.dto.InterviewRecordResponse;
import com.mienmien.business.management.application.security.BusinessRequestActor;
import com.mienmien.business.management.application.support.ApplicationSpaceGuard;
import com.mienmien.business.management.domain.DomainException;
import com.mienmien.business.management.domain.ResourceNotFoundException;
import com.mienmien.business.management.domain.model.InterviewRecord;
import com.mienmien.business.management.domain.model.InterviewRecordWithMeta;
import com.mienmien.business.management.domain.model.JobPosition;
import com.mienmien.business.management.domain.repository.InterviewRecordRepository;
import com.mienmien.business.management.domain.repository.JobPositionRepository;
import com.mienmien.business.management.domain.repository.SpaceRepository;
import com.mienmien.business.management.domain.support.ShortIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class InterviewApplicationService {
    private final SpaceRepository spaceRepository;
    private final ShortIdGenerator shortIdGenerator;
    private final InterviewRecordRepository interviewRecordRepository;
    private final JobPositionRepository jobPositionRepository;

    public InterviewApplicationService(
            SpaceRepository spaceRepository,
            ShortIdGenerator shortIdGenerator,
            InterviewRecordRepository interviewRecordRepository,
            JobPositionRepository jobPositionRepository) {
        this.spaceRepository = spaceRepository;
        this.shortIdGenerator = shortIdGenerator;
        this.interviewRecordRepository = interviewRecordRepository;
        this.jobPositionRepository = jobPositionRepository;
    }

    @Transactional
    public InterviewRecordResponse create(
            String category,
            String spaceId,
            String interviewType,
            int round,
            int score,
            String result,
            String summary,
            String positionIdOrNull) {
        ApplicationSpaceGuard.requireOwnedActiveSpace(spaceId, spaceRepository);
        String bound = normalizeBoundPositionId(positionIdOrNull);
        if (bound != null) {
            validateBoundPosition(spaceId, bound);
        }
        String recordId = shortIdGenerator.newId("i");
        InterviewRecord record =
                InterviewRecord.create(recordId, spaceId, category, round, interviewType, score, result, summary);
        Instant createdAt = Instant.now();
        interviewRecordRepository.insert(record, createdAt, bound);
        return toResponse(record, createdAt, bound);
    }

    public List<InterviewRecordResponse> listBySpace(String spaceId) {
        ApplicationSpaceGuard.requireOwnedActiveSpace(spaceId, spaceRepository);
        return interviewRecordRepository.findBySpaceIdOrderByCreatedAtDesc(spaceId).stream()
                .map(InterviewApplicationService::toResponse)
                .toList();
    }

    /**
     * 更新指定面试记录；{@code positionIdOverride} 为 null 表示不修改岗位绑定，空字符串表示清除绑定。
     */
    @Transactional
    public InterviewRecordResponse update(
            String recordId,
            String spaceId,
            String interviewType,
            int round,
            int score,
            String result,
            String summary,
            String positionIdOverride) {
        ApplicationSpaceGuard.requireOwnedActiveSpace(spaceId, spaceRepository);
        InterviewRecordWithMeta existing =
                interviewRecordRepository
                        .findByRecordIdAndSpaceId(recordId, spaceId)
                        .orElseThrow(() -> new ResourceNotFoundException("面试记录不存在"));
        String bound = resolveBindingOnUpdate(existing.positionId(), positionIdOverride);
        if (bound != null) {
            validateBoundPosition(spaceId, bound);
        }
        InterviewRecord updated =
                InterviewRecord.restore(
                        recordId,
                        spaceId,
                        existing.record().getCategory(),
                        round,
                        interviewType == null ? "business" : interviewType,
                        score,
                        result == null ? "pending" : result,
                        summary == null ? "" : summary);
        interviewRecordRepository.update(updated, bound);
        return toResponse(updated, existing.createdAt(), bound);
    }

    private static String resolveBindingOnUpdate(String currentBound, String positionIdOverride) {
        if (positionIdOverride == null) {
            return normalizeBoundPositionId(currentBound);
        }
        if (positionIdOverride.isBlank()) {
            return null;
        }
        return positionIdOverride.trim();
    }

    private void validateBoundPosition(String spaceId, String positionId) {
        JobPosition jp =
                jobPositionRepository
                        .findById(positionId)
                        .orElseThrow(() -> new ResourceNotFoundException("岗位不存在"));
        if (!BusinessRequestActor.requireUserId().equals(jp.getUserId())) {
            throw new DomainException("BUS-4033", "无权绑定该岗位");
        }
        List<String> linked = jobPositionRepository.findSpaceIdsByPositionId(positionId);
        if (!linked.contains(spaceId)) {
            throw new DomainException("BUS-4001", "岗位未关联到当前空间");
        }
    }

    private static String normalizeBoundPositionId(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        return t.isEmpty() ? null : t;
    }

    private static InterviewRecordResponse toResponse(InterviewRecordWithMeta meta) {
        return toResponse(meta.record(), meta.createdAt(), meta.positionId());
    }

    private static InterviewRecordResponse toResponse(InterviewRecord r, Instant createdAt, String positionId) {
        return new InterviewRecordResponse(
                r.getRecordId(),
                r.getSpaceId(),
                r.getCategory(),
                r.getRound(),
                r.getInterviewType(),
                r.getScore(),
                r.getResult(),
                r.getSummary(),
                positionId,
                createdAt);
    }
}
