package com.mienmien.consumer.guidance.infrastructure.capability;

import com.mienmien.consumer.guidance.domain.capability.ClientQuestionEnrichmentPolicy;
import org.springframework.stereotype.Component;

/**
 * 默认无操作：直接信任客户端传入文本。可替换为真实 ASR/视觉模型适配器。
 */
@Component
public class NoOpClientQuestionEnrichmentPolicy implements ClientQuestionEnrichmentPolicy {
    @Override
    public String enrichVoiceQuestion(String sessionId, String clientProvidedText) {
        return clientProvidedText == null ? "" : clientProvidedText.trim();
    }

    @Override
    public String enrichPhotoQuestion(String sessionId, String clientProvidedText) {
        return clientProvidedText == null ? "" : clientProvidedText.trim();
    }
}
