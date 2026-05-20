package com.mienmien.business.management.domain.repository;

import com.mienmien.business.management.domain.model.JobPosition;

import java.util.List;
import java.util.Optional;

/**
 * 岗位主体与空间多对多（{@code mm_job_position} + {@code mm_job_position_space}）。
 */
public interface JobPositionRepository {

    void insert(JobPosition position, List<String> linkedSpaceIds);

    void update(JobPosition position);

    void deleteById(String positionId);

    Optional<JobPosition> findById(String positionId);

    List<JobPosition> findByOwnerUserIdOrderByUpdatedAtDesc(String ownerUserId);

    List<JobPosition> findByLinkedSpaceIdOrderByUpdatedAtDesc(String spaceId);

    List<String> findSpaceIdsByPositionId(String positionId);

    void addSpaceLink(String positionId, String spaceId);

    void removeSpaceLink(String positionId, String spaceId);

    void syncPrimarySpaceColumn(String positionId);
}
