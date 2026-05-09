package com.mienmien.business.management.domain.repository;

import com.mienmien.business.management.domain.model.JobPosition;

import java.util.List;
import java.util.Optional;

public interface JobPositionRepository {
    void save(JobPosition position);

    void update(JobPosition position);

    Optional<JobPosition> findById(String positionId);

    List<JobPosition> findBySpaceIdOrderByCreatedAtDesc(String spaceId);

    long countBySpaceId(String spaceId);
}
