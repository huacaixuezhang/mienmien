package com.mienmien.consumer.guidance.application.diarization.engine;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class UnsupervisedEngine implements SpeakerDiarizationEngine {
    @Override
    public void init(Map<String, Object> config) {
        // 预留：VAD、Sortformer、Spring AI ASR 初始化
    }

    @Override
    public CompletableFuture<ProcessingResult> process(byte[] audioFrame) {
        long begin = System.currentTimeMillis();
        return CompletableFuture.supplyAsync(() -> {
            if (audioFrame == null || audioFrame.length == 0) {
                return null;
            }
            String text = "unsup:" + new String(audioFrame, 0, Math.min(audioFrame.length, 8), StandardCharsets.ISO_8859_1);
            return new ProcessingResult("speaker_0", text, 0.65d, System.currentTimeMillis(), true,
                    System.currentTimeMillis() - begin);
        });
    }

    @Override
    public void destroy() {
    }
}
