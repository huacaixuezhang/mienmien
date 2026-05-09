package com.mienmien.business.management.application.service;

import com.mienmien.business.management.application.dto.ResumeResponse;
import com.mienmien.business.management.application.policy.SpaceAccessPolicy;
import com.mienmien.business.management.application.security.BusinessRequestActor;
import com.mienmien.business.management.domain.DomainException;
import com.mienmien.business.management.domain.model.Resume;
import com.mienmien.business.management.domain.repository.ResumeRepository;
import com.mienmien.business.management.domain.support.ShortIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ResumeApplicationService {
    private final ResumeRepository resumeRepository;
    private final SpaceAccessPolicy spaceAccessPolicy;
    private final ShortIdGenerator idGenerator;

    public ResumeApplicationService(
            ResumeRepository resumeRepository,
            SpaceAccessPolicy spaceAccessPolicy,
            ShortIdGenerator idGenerator) {
        this.resumeRepository = resumeRepository;
        this.spaceAccessPolicy = spaceAccessPolicy;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public ResumeResponse createResume(String spaceId, String content, int version) {
        spaceAccessPolicy.requireWritableSpaceForActor(spaceId, BusinessRequestActor.requireUserId());
        if (resumeRepository.existsBySpaceIdAndVersion(spaceId, version)) {
            throw new DomainException("BUS-4091", "该空间下简历版本已存在: " + version);
        }
        Resume resume = Resume.createNew(idGenerator.newId("re_"), spaceId, version, content == null ? "" : content);
        resumeRepository.save(resume);
        return ResumeResponse.from(resume);
    }

    @Transactional(readOnly = true)
    public List<ResumeResponse> listBySpace(String spaceId) {
        spaceAccessPolicy.requireReadableSpaceForActor(spaceId, BusinessRequestActor.requireUserId());
        return resumeRepository.findBySpaceIdOrderByVersionDesc(spaceId).stream()
                .map(ResumeResponse::from)
                .toList();
    }
}
