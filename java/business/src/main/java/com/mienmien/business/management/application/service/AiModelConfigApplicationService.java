package com.mienmien.business.management.application.service;

import com.mienmien.business.management.application.dto.AiModelConfigResponse;
import com.mienmien.business.management.application.policy.SpaceAccessPolicy;
import com.mienmien.business.management.application.security.BusinessRequestActor;
import com.mienmien.business.management.domain.model.AiModelConfig;
import com.mienmien.business.management.domain.repository.AiModelConfigRepository;
import com.mienmien.business.management.domain.support.ShortIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AiModelConfigApplicationService {
    private final AiModelConfigRepository aiModelConfigRepository;
    private final SpaceAccessPolicy spaceAccessPolicy;
    private final ShortIdGenerator shortIdGenerator;

    public AiModelConfigApplicationService(
            AiModelConfigRepository aiModelConfigRepository,
            SpaceAccessPolicy spaceAccessPolicy,
            ShortIdGenerator shortIdGenerator) {
        this.aiModelConfigRepository = aiModelConfigRepository;
        this.spaceAccessPolicy = spaceAccessPolicy;
        this.shortIdGenerator = shortIdGenerator;
    }

    @Transactional(readOnly = true)
    public AiModelConfigResponse getBySpace(String spaceId) {
        spaceAccessPolicy.requireReadableSpaceForActor(spaceId, BusinessRequestActor.requireUserId());
        return aiModelConfigRepository.findBySpaceId(spaceId)
                .map(AiModelConfigResponse::from)
                .orElseGet(() -> AiModelConfigResponse.from(AiModelConfig.createNew(
                        shortIdGenerator.newId("ac_"),
                        spaceId,
                        "aliyun-bailian",
                        "",
                        "",
                        ""
                )));
    }

    @Transactional
    public AiModelConfigResponse upsert(
            String spaceId,
            String provider,
            String baseUrl,
            String apiKey,
            String modelName) {
        spaceAccessPolicy.requireWritableSpaceForActor(spaceId, BusinessRequestActor.requireUserId());
        Optional<AiModelConfig> existing = aiModelConfigRepository.findBySpaceId(spaceId);
        AiModelConfig config = existing.orElseGet(() -> AiModelConfig.createNew(
                shortIdGenerator.newId("ac_"),
                spaceId,
                provider,
                baseUrl,
                apiKey,
                modelName
        ));
        if (existing.isPresent()) {
            config.update(provider, baseUrl, apiKey, modelName);
            aiModelConfigRepository.update(config);
        } else {
            aiModelConfigRepository.save(config);
        }
        return AiModelConfigResponse.from(config);
    }
}
