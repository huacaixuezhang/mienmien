package com.mienmien.consumer.guidance.domain.capability;

/**
 * 客户端问题的增强策略（语音识别、图像理解等），由基础设施实现可替换适配器。
 */
public interface ClientQuestionEnrichmentPolicy {
    String enrichVoiceQuestion(String sessionId, String clientProvidedText);

    String enrichPhotoQuestion(String sessionId, String clientProvidedText);
}
