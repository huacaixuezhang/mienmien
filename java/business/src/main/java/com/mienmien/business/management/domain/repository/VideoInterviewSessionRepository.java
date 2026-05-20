package com.mienmien.business.management.domain.repository;

import com.mienmien.business.management.domain.model.VideoInterviewSession;

import java.util.Optional;

public interface VideoInterviewSessionRepository {
    void insert(VideoInterviewSession session);

    Optional<VideoInterviewSession> findBySessionId(String sessionId);
}
