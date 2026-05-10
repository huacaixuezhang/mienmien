package com.mienmien.business.management.application.dto;

import java.time.Instant;

/** 模型连接配置为「登录用户」维度，与空间无关；同一用户下所有空间共享。 */
public record AiModelConfigResponse(
        String configId,
        String ownerUserId,
        String provider,
        String baseUrl,
        String apiKey,
        String modelName,
        Instant updatedAt
) {
}
