package com.mienmien.consumer.videointerview.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * OpenAI 兼容 Chat Completions（百炼 compatible-mode），供判停与导演模型调用。
 */
@Component
public class DashscopeCompatibleChatClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

    public DashscopeCompatibleChatClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${spring.ai.dashscope.api-key:}") String apiKey) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public boolean hasApiKey() {
        return !apiKey.isBlank();
    }

    public Optional<String> complete(String baseUrl, String modelName, String systemPrompt, String userPrompt, int maxTokens) {
        if (!hasApiKey()) {
            return Optional.empty();
        }
        String url = resolveOpenAiChatCompletionsUrl(baseUrl);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("max_tokens", Math.min(Math.max(maxTokens, 128), 8192));
        body.put("stream", false);
        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt == null ? "" : userPrompt));
        body.put("messages", messages);
        try {
            String json =
                    restClient
                            .post()
                            .uri(url)
                            .headers(h -> h.addAll(headers))
                            .body(body)
                            .retrieve()
                            .body(String.class);
            return Optional.of(parseAssistantText(json));
        } catch (RestClientException e) {
            return Optional.empty();
        } catch (Exception e) {
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

    private String parseAssistantText(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return "";
        }
        JsonNode root = objectMapper.readTree(json);
        if (root.has("error")) {
            return "";
        }
        JsonNode textNode = root.path("choices").path(0).path("message").path("content");
        if (textNode.isMissingNode() || textNode.isNull()) {
            return "";
        }
        return textNode.asText("").trim();
    }

    /**
     * OpenAI 兼容流式 chat/completions：按 SSE 增量回调 {@code onDelta}，返回拼接后的全文。
     */
    public String streamComplete(
            String baseUrl,
            String modelName,
            String systemPrompt,
            String userPrompt,
            int maxTokens,
            Consumer<String> onDelta)
            throws Exception {
        if (!hasApiKey()) {
            return "";
        }
        String url = resolveOpenAiChatCompletionsUrl(baseUrl);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("max_tokens", Math.min(Math.max(maxTokens, 128), 8192));
        body.put("stream", true);
        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt == null ? "" : userPrompt));
        body.put("messages", messages);
        String jsonBody = objectMapper.writeValueAsString(body);
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofMinutes(5))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                        .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 400) {
            response.body().close();
            return "";
        }
        StringBuilder full = new StringBuilder();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if ("[DONE]".equals(data)) {
                    break;
                }
                String delta = extractStreamDeltaContent(data);
                if (delta != null && !delta.isEmpty()) {
                    full.append(delta);
                    if (onDelta != null) {
                        onDelta.accept(delta);
                    }
                }
            }
        }
        return full.toString().trim();
    }

    private String extractStreamDeltaContent(String dataJson) {
        try {
            JsonNode root = objectMapper.readTree(dataJson);
            JsonNode delta = root.path("choices").path(0).path("delta");
            JsonNode content = delta.path("content");
            if (content.isMissingNode() || content.isNull()) {
                return "";
            }
            if (content.isTextual()) {
                return content.asText("");
            }
            if (content.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode part : content) {
                    if (part == null || !part.isObject()) {
                        continue;
                    }
                    String t = part.path("text").asText("");
                    if (!t.isEmpty()) {
                        sb.append(t);
                    } else {
                        String alt = part.path("content").asText("");
                        if (!alt.isEmpty()) {
                            sb.append(alt);
                        }
                    }
                }
                return sb.toString();
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }
}
