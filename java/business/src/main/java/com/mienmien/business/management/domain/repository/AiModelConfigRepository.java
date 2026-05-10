package com.mienmien.business.management.domain.repository;

import com.mienmien.business.management.domain.model.AiModelConfig;

import java.util.Optional;

public interface AiModelConfigRepository {
    Optional<AiModelConfig> findByOwnerUserId(String ownerUserId);

    void save(AiModelConfig config);

    void update(AiModelConfig config);
}
