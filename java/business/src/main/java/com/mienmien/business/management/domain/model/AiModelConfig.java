package com.mienmien.business.management.domain.model;

import java.time.Instant;
import java.util.Objects;

public final class AiModelConfig {
    private final String configId;
    private final String ownerUserId;
    private String provider;
    private String baseUrl;
    private String apiKey;
    private String modelName;
    private Instant updatedAt;

    private AiModelConfig(
            String configId,
            String ownerUserId,
            String provider,
            String baseUrl,
            String apiKey,
            String modelName,
            Instant updatedAt) {
        this.configId = Objects.requireNonNull(configId);
        this.ownerUserId = Objects.requireNonNull(ownerUserId);
        this.provider = normalize(provider, "aliyun-bailian");
        this.baseUrl = normalize(baseUrl, "");
        this.apiKey = normalize(apiKey, "");
        this.modelName = normalize(modelName, "");
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static AiModelConfig createNew(
            String configId,
            String ownerUserId,
            String provider,
            String baseUrl,
            String apiKey,
            String modelName) {
        return new AiModelConfig(configId, ownerUserId, provider, baseUrl, apiKey, modelName, Instant.now());
    }

    public static AiModelConfig restore(
            String configId,
            String ownerUserId,
            String provider,
            String baseUrl,
            String apiKey,
            String modelName,
            Instant updatedAt) {
        return new AiModelConfig(configId, ownerUserId, provider, baseUrl, apiKey, modelName, updatedAt);
    }

    public void update(String provider, String baseUrl, String apiKey, String modelName) {
        this.provider = normalize(provider, "aliyun-bailian");
        this.baseUrl = normalize(baseUrl, "");
        this.apiKey = normalize(apiKey, "");
        this.modelName = normalize(modelName, "");
        this.updatedAt = Instant.now();
    }

    private static String normalize(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    public String getConfigId() {
        return configId;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public String getProvider() {
        return provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getModelName() {
        return modelName;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
