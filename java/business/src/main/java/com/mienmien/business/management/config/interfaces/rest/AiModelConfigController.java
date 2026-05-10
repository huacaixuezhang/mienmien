package com.mienmien.business.management.config.interfaces.rest;

import com.mienmien.business.management.application.dto.AiModelConfigResponse;
import com.mienmien.business.management.application.dto.ModelConfigTestResponse;
import com.mienmien.business.management.application.dto.TestModelConfigRequest;
import com.mienmien.business.management.application.service.AiModelConfigApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    /** 当前登录用户的模型配置（与空间无关）。 */
    @GetMapping("/me")
    public AiModelConfigResponse getMine() {
        return aiModelConfigApplicationService.getMine();
    }

    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    public AiModelConfigResponse upsert(@Valid @RequestBody UpsertAiModelConfigRequest req) {
        return aiModelConfigApplicationService.upsertMine(
                req.provider(), req.baseUrl(), req.apiKey(), req.modelName());
    }

    @PostMapping("/test")
    public ModelConfigTestResponse test(@Valid @RequestBody TestModelConfigRequest req) {
        return aiModelConfigApplicationService.testChatMine(req.testPrompt(), req.modelName());
    }

    public record UpsertAiModelConfigRequest(String provider, String baseUrl, String apiKey, String modelName) {
    }
}
