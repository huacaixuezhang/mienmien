package com.mienmien.business.management.application.service;

import com.mienmien.business.management.application.dto.JobPositionResponse;
import com.mienmien.business.management.application.policy.SpaceAccessPolicy;
import com.mienmien.business.management.application.security.BusinessRequestActor;
import com.mienmien.business.management.domain.ResourceNotFoundException;
import com.mienmien.business.management.domain.model.JobPosition;
import com.mienmien.business.management.domain.repository.JobPositionRepository;
import com.mienmien.business.management.domain.support.ShortIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class JobPositionApplicationService {
    private final JobPositionRepository jobPositionRepository;
    private final SpaceAccessPolicy spaceAccessPolicy;
    private final ShortIdGenerator idGenerator;

    public JobPositionApplicationService(
            JobPositionRepository jobPositionRepository,
            SpaceAccessPolicy spaceAccessPolicy,
            ShortIdGenerator idGenerator) {
        this.jobPositionRepository = jobPositionRepository;
        this.spaceAccessPolicy = spaceAccessPolicy;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public JobPositionResponse create(String spaceId, String title, String company, String location, String baseRange) {
        spaceAccessPolicy.requireWritableSpaceForActor(spaceId, BusinessRequestActor.requireUserId());
        JobPosition p = JobPosition.createNew(idGenerator.newId("jp_"), spaceId, title, company, location, baseRange);
        jobPositionRepository.save(p);
        return JobPositionResponse.from(p);
    }

    @Transactional(readOnly = true)
    public List<JobPositionResponse> listBySpace(String spaceId) {
        spaceAccessPolicy.requireReadableSpaceForActor(spaceId, BusinessRequestActor.requireUserId());
        return jobPositionRepository.findBySpaceIdOrderByCreatedAtDesc(spaceId).stream()
                .map(JobPositionResponse::from)
                .toList();
    }

    @Transactional
    public JobPositionResponse update(
            String positionId,
            String title,
            String company,
            String location,
            String baseRange) {
        JobPosition p = jobPositionRepository.findById(positionId)
                .orElseThrow(() -> new ResourceNotFoundException("岗位不存在: " + positionId));
        spaceAccessPolicy.requireWritableSpaceForActor(p.getSpaceId(), BusinessRequestActor.requireUserId());
        p.updateProfile(title, company, location, baseRange);
        jobPositionRepository.update(p);
        return JobPositionResponse.from(p);
    }

    @Transactional
    public JobPositionResponse close(String positionId) {
        JobPosition p = jobPositionRepository.findById(positionId)
                .orElseThrow(() -> new ResourceNotFoundException("岗位不存在: " + positionId));
        spaceAccessPolicy.requireWritableSpaceForActor(p.getSpaceId(), BusinessRequestActor.requireUserId());
        p.markClosed();
        jobPositionRepository.update(p);
        return JobPositionResponse.from(p);
    }
}
