package com.mienmien.business.management.domain.repository;

import com.mienmien.business.management.domain.model.JdTarget;

import java.util.List;

public interface JdTargetRepository {
    void save(JdTarget jdTarget);

    List<JdTarget> findBySpaceIdOrderByCreatedAtDesc(String spaceId);

    long countBySpaceId(String spaceId);
}
