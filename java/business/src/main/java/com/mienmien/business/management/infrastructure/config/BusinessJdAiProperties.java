package com.mienmien.business.management.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 百炼等大模型 HTTP 调用参数（与 {@code application.yml} 中 {@code mienmien.business.jd-ai} 对应）。
 * <p>官方说明见百炼控制台「API-Key 调用」：
 * <a href="https://bailian.console.aliyun.com/cn-beijing?tab=api#/api">大模型服务平台百炼控制台</a>；
 * 兼容 OpenAI 的 HTTP 形态可参考阿里云「通过 HTTP 调用」文档中的 compatible-mode 地址与鉴权方式。
 */
@ConfigurationProperties(prefix = "mienmien.business.jd-ai")
public record BusinessJdAiProperties(String apiUrl, String apiKey, String model) {
    public BusinessJdAiProperties {
        apiUrl = apiUrl == null ? "" : apiUrl.trim();
        apiKey = apiKey == null ? "" : apiKey.trim();
        model = model == null || model.isBlank() ? "qwen-plus" : model.trim();
    }
}
