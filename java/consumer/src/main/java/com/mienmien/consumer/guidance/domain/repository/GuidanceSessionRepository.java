package com.mienmien.consumer.guidance.domain.repository;

import com.mienmien.consumer.guidance.domain.model.GuidanceSession;

import java.time.Instant;
import java.util.Optional;

public interface GuidanceSessionRepository {
    void save(GuidanceSession session);

    void updateStatus(String sessionId, String status);

    Optional<GuidanceSession> findById(String sessionId);

    void endSession(String sessionId, Instant endedAt);
}
