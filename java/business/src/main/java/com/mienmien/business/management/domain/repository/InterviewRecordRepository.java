package com.mienmien.business.management.domain.repository;

import com.mienmien.business.management.domain.model.InterviewRecord;
import com.mienmien.business.management.domain.model.InterviewRecordWithMeta;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InterviewRecordRepository {

    void insert(InterviewRecord record, Instant createdAt, String boundPositionIdOrNull);

    void update(InterviewRecord record, String boundPositionIdOrNull);

    List<InterviewRecordWithMeta> findBySpaceIdOrderByCreatedAtDesc(String spaceId);

    Optional<InterviewRecordWithMeta> findByRecordIdAndSpaceId(String recordId, String spaceId);

    long countBySpaceId(String spaceId);
}
