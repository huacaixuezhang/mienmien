package com.mienmien.business.management.application.dto;

import com.mienmien.business.management.domain.model.AiModelConfig;

import java.time.Instant;

public record AiModelConfigResponse(
        String configId,
        String spaceId,
        String provider,
        String baseUrl,
        String apiKey,
        String modelName,
        Instant updatedAt) {
    public static AiModelConfigResponse from(AiModelConfig config) {
        return new AiModelConfigResponse(
                config.getConfigId(),
                config.getSpaceId(),
                config.getProvider(),
                config.getBaseUrl(),
                config.getApiKey(),
                config.getModelName(),
                config.getUpdatedAt()
        );
    }
}
