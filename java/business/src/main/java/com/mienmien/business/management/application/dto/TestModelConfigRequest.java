package com.mienmien.business.management.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

/**
 * 模型连接测试请求：按当前登录用户读库中配置，无需 spaceId。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TestModelConfigRequest(@NotBlank String testPrompt, String modelName) {
}
