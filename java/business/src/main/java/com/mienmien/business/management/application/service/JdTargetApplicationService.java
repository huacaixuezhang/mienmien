package com.mienmien.business.management.application.service;

import com.mienmien.business.management.application.dto.JdTargetResponse;
import com.mienmien.business.management.application.capability.JdFocusPointAnalyzer;
import com.mienmien.business.management.application.policy.SpaceAccessPolicy;
import com.mienmien.business.management.application.security.BusinessRequestActor;
import com.mienmien.business.management.domain.model.JdTarget;
import com.mienmien.business.management.domain.repository.JdTargetRepository;
import com.mienmien.business.management.domain.support.ShortIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class JdTargetApplicationService {
    private final JdTargetRepository jdTargetRepository;
    private final SpaceAccessPolicy spaceAccessPolicy;
    private final ShortIdGenerator idGenerator;
    private final JdFocusPointAnalyzer jdFocusPointAnalyzer;

    public JdTargetApplicationService(
            JdTargetRepository jdTargetRepository,
            SpaceAccessPolicy spaceAccessPolicy,
            ShortIdGenerator idGenerator,
            JdFocusPointAnalyzer jdFocusPointAnalyzer) {
        this.jdTargetRepository = jdTargetRepository;
        this.spaceAccessPolicy = spaceAccessPolicy;
        this.idGenerator = idGenerator;
        this.jdFocusPointAnalyzer = jdFocusPointAnalyzer;
    }

    @Transactional
    public JdTargetResponse create(String spaceId, String rawText, String focusPoints) {
        spaceAccessPolicy.requireWritableSpaceForActor(spaceId, BusinessRequestActor.requireUserId());
        JdTarget jd = JdTarget.createManual(idGenerator.newId("jd_"), spaceId, rawText, focusPoints);
        jdTargetRepository.save(jd);
        return JdTargetResponse.from(jd);
    }

    @Transactional(readOnly = true)
    public List<JdTargetResponse> listBySpace(String spaceId) {
        spaceAccessPolicy.requireReadableSpaceForActor(spaceId, BusinessRequestActor.requireUserId());
        return jdTargetRepository.findBySpaceIdOrderByCreatedAtDesc(spaceId).stream()
                .map(JdTargetResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public String analyzeFocusPoints(String rawText) {
        return jdFocusPointAnalyzer.analyze(rawText);
    }
}
