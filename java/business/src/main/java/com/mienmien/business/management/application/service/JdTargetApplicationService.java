package com.mienmien.business.management.application.service;

import com.mienmien.business.management.application.capability.JdFocusPointAnalyzer;
import com.mienmien.business.management.application.dto.JdTargetResponse;
import com.mienmien.business.management.application.support.ApplicationSpaceGuard;
import com.mienmien.business.management.domain.model.JdTarget;
import com.mienmien.business.management.domain.repository.SpaceRepository;
import com.mienmien.business.management.domain.support.ShortIdGenerator;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class JdTargetApplicationService {
    private final SpaceRepository spaceRepository;
    private final ShortIdGenerator shortIdGenerator;
    private final JdFocusPointAnalyzer jdFocusPointAnalyzer;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<StoredJd>> bySpace = new ConcurrentHashMap<>();

    public JdTargetApplicationService(
            SpaceRepository spaceRepository,
            ShortIdGenerator shortIdGenerator,
            JdFocusPointAnalyzer jdFocusPointAnalyzer) {
        this.spaceRepository = spaceRepository;
        this.shortIdGenerator = shortIdGenerator;
        this.jdFocusPointAnalyzer = jdFocusPointAnalyzer;
    }

    public JdTargetResponse create(String spaceId, String rawText, String focusPoints) {
        ApplicationSpaceGuard.requireOwnedActiveSpace(spaceId, spaceRepository);
        String jdId = shortIdGenerator.newId("j");
        JdTarget jd = JdTarget.createManual(jdId, spaceId, rawText, focusPoints);
        StoredJd stored = new StoredJd(jd, Instant.now());
        bySpace.computeIfAbsent(spaceId, k -> new CopyOnWriteArrayList<>()).add(stored);
        return toResponse(stored);
    }

    public List<JdTargetResponse> listBySpace(String spaceId) {
        ApplicationSpaceGuard.requireOwnedActiveSpace(spaceId, spaceRepository);
        CopyOnWriteArrayList<StoredJd> list = bySpace.get(spaceId);
        if (list == null) {
            return List.of();
        }
        return list.stream().map(JdTargetApplicationService::toResponse).toList();
    }

    public String analyzeFocusPoints(String rawText) {
        return jdFocusPointAnalyzer.analyzeFocusPoints(rawText);
    }

    private static JdTargetResponse toResponse(StoredJd s) {
        JdTarget j = s.jd();
        return new JdTargetResponse(
                j.getJdId(),
                j.getSpaceId(),
                j.getSourceType(),
                j.getRawText(),
                j.getFocusPoints(),
                s.createdAt()
        );
    }

    private record StoredJd(JdTarget jd, Instant createdAt) {
    }
}
