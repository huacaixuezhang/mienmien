package com.mienmien.business.management.domain.repository;

import com.mienmien.business.management.domain.model.InterviewRecord;

import java.util.List;

public interface InterviewRecordRepository {
    void save(InterviewRecord record);

    List<InterviewRecord> findBySpaceIdOrderByCreatedAtDesc(String spaceId);

    long countBySpaceId(String spaceId);
}
