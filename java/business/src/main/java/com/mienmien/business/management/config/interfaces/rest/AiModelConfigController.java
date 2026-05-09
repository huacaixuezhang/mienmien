package com.mienmien.business.management.config.interfaces.rest;

import com.mienmien.business.management.application.dto.AiModelConfigResponse;
import com.mienmien.business.management.application.service.AiModelConfigApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/business/model-configs")
public class AiModelConfigController {
    private final AiModelConfigApplicationService aiModelConfigApplicationService;

    public AiModelConfigController(AiModelConfigApplicationService aiModelConfigApplicationService) {
        this.aiModelConfigApplicationService = aiModelConfigApplicationService;
    }

    @GetMapping("/{spaceId}")
    public AiModelConfigResponse getBySpace(@PathVariable("spaceId") String spaceId) {
        return aiModelConfigApplicationService.getBySpace(spaceId);
    }

    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    public AiModelConfigResponse upsert(@Valid @RequestBody UpsertAiModelConfigRequest req) {
        return aiModelConfigApplicationService.upsert(
                req.spaceId(),
                req.provider(),
                req.baseUrl(),
                req.apiKey(),
                req.modelName()
        );
    }

    public record UpsertAiModelConfigRequest(
            @NotBlank String spaceId,
            String provider,
            String baseUrl,
            String apiKey,
            String modelName) {
    }
}
