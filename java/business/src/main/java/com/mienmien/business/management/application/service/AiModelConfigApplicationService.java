package com.mienmien.business.management.application.service;

import com.mienmien.business.management.application.dto.AiModelConfigResponse;
import com.mienmien.business.management.application.dto.ModelConfigTestResponse;
import com.mienmien.business.management.application.security.BusinessRequestActor;
import com.mienmien.business.management.domain.DomainException;
import com.mienmien.business.management.domain.model.AiModelConfig;
import com.mienmien.business.management.domain.repository.AiModelConfigRepository;
import com.mienmien.business.management.infrastructure.capability.BailianLlmClient;
import com.mienmien.business.management.infrastructure.crypto.RsaAsymmetricCryptoService;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AiModelConfigApplicationService {
    private static final String API_KEY_MASK = "********";

    private final AiModelConfigRepository aiModelConfigRepository;
    private final BailianLlmClient bailianLlmClient;
    private final RsaAsymmetricCryptoService rsaAsymmetricCryptoService;

    public AiModelConfigApplicationService(
            AiModelConfigRepository aiModelConfigRepository,
            BailianLlmClient bailianLlmClient,
            RsaAsymmetricCryptoService rsaAsymmetricCryptoService) {
        this.aiModelConfigRepository = aiModelConfigRepository;
        this.bailianLlmClient = bailianLlmClient;
        this.rsaAsymmetricCryptoService = rsaAsymmetricCryptoService;
    }

    /** 当前登录用户的模型配置（与空间无关，全空间共享）。 */
    public AiModelConfigResponse getMine() {
        String userId = BusinessRequestActor.requireUserId();
        return aiModelConfigRepository
                .findByOwnerUserId(userId)
                .map(AiModelConfigApplicationService::toResponse)
                .orElseGet(
                        () ->
                                new AiModelConfigResponse(
                                        "mc_" + userId, userId, "aliyun-bailian", "", "", false, "", Instant.now()));
    }

    public AiModelConfigResponse upsertMine(String provider, String baseUrl, String apiKeyCipher, String modelName) {
        String userId = BusinessRequestActor.requireUserId();
        return aiModelConfigRepository
                .findByOwnerUserId(userId)
                .map(
                        existing -> {
                            String nextApiKey = resolveStoredApiKey(existing.getApiKey(), apiKeyCipher);
                            existing.update(provider, baseUrl, nextApiKey, modelName);
                            aiModelConfigRepository.update(existing);
                            return toResponse(existing);
                        })
                .orElseGet(
                        () -> {
                            String configId = "mc_" + userId;
                            String sealed = sealApiKeyForStorage(apiKeyCipher);
                            AiModelConfig created =
                                    AiModelConfig.createNew(configId, userId, provider, baseUrl, sealed, modelName);
                            aiModelConfigRepository.save(created);
                            return toResponse(created);
                        });
    }

    /** 使用当前用户在库中的 Base URL 与 API Key，按请求中的提示词与模型名调用大模型。 */
    public ModelConfigTestResponse testChatMine(String testPrompt, String modelNameFromClient) {
        String userId = BusinessRequestActor.requireUserId();
        AiModelConfig cfg =
                aiModelConfigRepository
                        .findByOwnerUserId(userId)
                        .orElseThrow(
                                () ->
                                        new DomainException(
                                                "BUS-4001", "请先在系统设置中保存模型连接配置"));
        String plainApiKey = rsaAsymmetricCryptoService.unsealFromStorage(cfg.getApiKey());
        if (plainApiKey.isBlank()) {
            throw new DomainException("BUS-4001", "请先在系统设置中配置 API Key 并保存");
        }
        if (cfg.getBaseUrl().isBlank()) {
            throw new DomainException("BUS-4001", "请先在系统设置中配置 Base URL 并保存");
        }
        String prompt = testPrompt == null ? "" : testPrompt.trim();
        if (prompt.isBlank()) {
            throw new DomainException("BUS-4001", "测试提示词不能为空");
        }
        String model =
                modelNameFromClient == null || modelNameFromClient.isBlank()
                        ? cfg.getModelName()
                        : modelNameFromClient.trim();
        if (model.isBlank()) {
            throw new DomainException("BUS-4001", "请填写用于测试调用的模型名称");
        }
        String text =
                bailianLlmClient.completeUserPrompt(cfg.getBaseUrl(), plainApiKey, model, prompt);
        return new ModelConfigTestResponse(text);
    }

    private String resolveStoredApiKey(String existingStored, String apiKeyCipher) {
        if (apiKeyCipher == null || apiKeyCipher.isBlank()) {
            return existingStored == null ? "" : existingStored;
        }
        if (RsaAsymmetricCryptoService.looksLikeMaskedPlaceholder(apiKeyCipher)) {
            return existingStored == null ? "" : existingStored;
        }
        return sealApiKeyForStorage(apiKeyCipher);
    }

    private String sealApiKeyForStorage(String apiKeyCipher) {
        String plain = rsaAsymmetricCryptoService.resolveInboundSecret(apiKeyCipher);
        if (plain.isBlank()) {
            return "";
        }
        return rsaAsymmetricCryptoService.sealForStorage(plain);
    }

    private static AiModelConfigResponse toResponse(AiModelConfig c) {
        String stored = c.getApiKey() == null ? "" : c.getApiKey().trim();
        boolean configured = !stored.isBlank();
        return new AiModelConfigResponse(
                c.getConfigId(),
                c.getOwnerUserId(),
                c.getProvider(),
                c.getBaseUrl(),
                configured ? API_KEY_MASK : "",
                configured,
                c.getModelName(),
                c.getUpdatedAt());
    }
}
