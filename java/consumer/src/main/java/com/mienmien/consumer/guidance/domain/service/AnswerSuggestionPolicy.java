package com.mienmien.consumer.guidance.domain.service;

import java.util.List;

/**
 * 领域策略：根据用户问题生成回答要点（可替换为真实模型推理）。
 */
public final class AnswerSuggestionPolicy {
    private AnswerSuggestionPolicy() {
    }

    public static List<String> progressiveChunks() {
        return List.of(
                "收到问题，正在分析...",
                "识别关键词：项目经历、成果、协作"
        );
    }

    public static String composeFinalAnswer(String questionText) {
        String q = questionText == null ? "" : questionText;
        return "建议回答结构：背景-挑战-行动-结果。问题原文：" + q;
    }

    public static String photoQaAdvisory() {
        return "拍照问答建议：先描述场景，再提炼业务影响，最后给出行动方案。";
    }

    public static String fallbackOnceAnswer() {
        return "降级回答：请使用 STAR 模型描述一个最有代表性的项目。";
    }

    public static String streamFallbackMessage() {
        return "服务繁忙，降级为一次性建议回答。";
    }
}
