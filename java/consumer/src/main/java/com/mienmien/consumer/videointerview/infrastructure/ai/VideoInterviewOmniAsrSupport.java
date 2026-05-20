package com.mienmien.consumer.videointerview.infrastructure.ai;

import com.alibaba.dashscope.audio.omni.OmniRealtimeConfig;
import com.alibaba.dashscope.audio.omni.OmniRealtimeModality;
import com.alibaba.dashscope.audio.omni.OmniRealtimeParam;
import com.alibaba.dashscope.audio.omni.OmniRealtimeTranscriptionParam;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Optional;

/**
 * 百炼 Qwen-ASR-Realtime（Omni WebSocket）建连参数与事件解析辅助。
 */
public final class VideoInterviewOmniAsrSupport {

    private VideoInterviewOmniAsrSupport() {}

    public static OmniRealtimeParam buildParam(String model, String wsUrl, String apiKey) {
        return OmniRealtimeParam.builder()
                .model(model)
                .url(wsUrl)
                .apikey(apiKey)
                .build();
    }

    public static OmniRealtimeConfig buildConfig() {
        OmniRealtimeTranscriptionParam transcriptionParam = new OmniRealtimeTranscriptionParam();
        transcriptionParam.setLanguage("zh");
        transcriptionParam.setInputSampleRate(16000);
        transcriptionParam.setInputAudioFormat("pcm");

        return OmniRealtimeConfig.builder()
                .modalities(List.of(OmniRealtimeModality.TEXT))
                .enableInputAudioTranscription(true)
                .enableTurnDetection(true)
                .turnDetectionType("server_vad")
                .turnDetectionThreshold(0.0f)
                .turnDetectionSilenceDurationMs(400)
                .transcriptionConfig(transcriptionParam)
                .build();
    }

    /** 从 Omni 下行 JSON 中尽量提取转写文本（兼容字段差异）。 */
    public static Optional<String> extractTranscriptText(JsonObject root) {
        if (root == null) {
            return Optional.empty();
        }
        Optional<String> top = textPrimitive(root, "transcript");
        if (top.isPresent()) {
            return top;
        }
        top = textPrimitive(root, "text");
        if (top.isPresent()) {
            return top;
        }
        for (String k : new String[] {"item", "delta", "output", "response"}) {
            if (!root.has(k) || root.get(k) == null || !root.get(k).isJsonObject()) {
                continue;
            }
            JsonObject o = root.getAsJsonObject(k);
            Optional<String> inner = textPrimitive(o, "transcript").or(() -> textPrimitive(o, "text"));
            if (inner.isPresent()) {
                return inner;
            }
        }
        return Optional.empty();
    }

    public static boolean isTranscriptionCompletedEvent(String type) {
        if (type == null) {
            return false;
        }
        return type.contains("input_audio_transcription")
                && (type.contains("completed") || type.contains("done") || type.contains("final"));
    }

    public static boolean isTranscriptionPartialEvent(String type) {
        if (type == null) {
            return false;
        }
        return type.contains("input_audio_transcription")
                && (type.contains("text") || type.contains("delta") || type.contains("partial"));
    }

    private static Optional<String> textPrimitive(JsonObject o, String field) {
        if (!o.has(field)) {
            return Optional.empty();
        }
        JsonElement e = o.get(field);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) {
            return Optional.empty();
        }
        String s = e.getAsString();
        if (s == null || s.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(s);
    }
}
