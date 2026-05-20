package com.mienmien.consumer.videointerview.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mienmien.consumer.videointerview.config.VideoInterviewProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 百炼 Qwen3-ASR-Flash（OpenAI 兼容 chat/completions + input_audio），用于视频面试句末转写。
 *
 * @see <a href="https://help.aliyun.com/zh/model-studio/">Model Studio 文档</a>
 */
@Component
public class DashscopeQwenAsrClient {

    private static final Logger log = LoggerFactory.getLogger(DashscopeQwenAsrClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final VideoInterviewProperties videoInterviewProperties;
    private final String apiKey;

    public DashscopeQwenAsrClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            VideoInterviewProperties videoInterviewProperties,
            @Value("${spring.ai.dashscope.api-key:}") String apiKey) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.videoInterviewProperties = videoInterviewProperties;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    /**
     * @param audioBytes 原始音频字节（推荐 WAV 单声道 PCM16）
     * @param mimeType 用于构造 Data URL，如 audio/wav、audio/mpeg
     */
    public Optional<String> transcribe(byte[] audioBytes, String mimeType) {
        if (!isConfigured() || audioBytes == null || audioBytes.length == 0) {
            return Optional.empty();
        }
        String mime = mimeType == null || mimeType.isBlank() ? "audio/wav" : mimeType.split(";")[0].trim();
        String b64 = Base64.getEncoder().encodeToString(audioBytes);
        String dataUri = "data:" + mime + ";base64," + b64;

        String url = resolveOpenAiChatCompletionsUrl(videoInterviewProperties.dashscopeBaseUrl());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        List<Map<String, Object>> content = new ArrayList<>();
        content.add(
                Map.of(
                        "type",
                        "input_audio",
                        "input_audio",
                        Map.of("data", dataUri)));
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", content));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", videoInterviewProperties.qwenAsrModel());
        body.put("messages", messages);
        body.put("stream", false);
        body.put("asr_options", Map.of("language", "zh", "enable_itn", true));

        try {
            String json =
                    restClient
                            .post()
                            .uri(url)
                            .headers(h -> h.addAll(headers))
                            .body(body)
                            .retrieve()
                            .body(String.class);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(json);
            if (root.has("error")) {
                log.warn("DashScope ASR error payload: {}", root.get("error"));
                return Optional.empty();
            }
            return Optional.of(extractTranscribedText(root));
        } catch (RestClientException e) {
            log.warn("DashScope ASR HTTP 调用失败: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("DashScope ASR 解析失败", e);
            return Optional.empty();
        }
    }

    private static String resolveOpenAiChatCompletionsUrl(String baseUrl) {
        String t = baseUrl.replaceAll("/+$", "");
        if (t.endsWith("/chat/completions")) {
            return t;
        }
        if (t.endsWith("/v1")) {
            return t + "/chat/completions";
        }
        return t + "/v1/chat/completions";
    }

    private static String extractTranscribedText(JsonNode root) {
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        if (contentNode.isTextual()) {
            return contentNode.asText("").trim();
        }
        if (contentNode.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : contentNode) {
                if ("text".equals(item.path("type").asText())) {
                    sb.append(item.path("text").asText(""));
                }
            }
            return sb.toString().trim();
        }
        return "";
    }
}
