package com.mienmien.business.management.infrastructure.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mienmien.business.management.domain.DomainException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务端调用百炼：支持 OpenAI 兼容 {@code .../chat/completions} 与 Anthropic 应用网关 {@code .../apps/anthropic/v1/messages}。
 */
@Component
public class BailianLlmClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public BailianLlmClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public String completeUserPrompt(String baseUrl, String apiKey, String modelName, String userPrompt) {
        String trimmedBase = baseUrl == null ? "" : baseUrl.trim();
        String trimmedKey = apiKey == null ? "" : apiKey.trim();
        String trimmedModel = modelName == null ? "" : modelName.trim();
        String trimmedPrompt = userPrompt == null ? "" : userPrompt.trim();
        if (trimmedBase.isEmpty() || trimmedKey.isEmpty() || trimmedModel.isEmpty() || trimmedPrompt.isEmpty()) {
            throw new DomainException("BUS-4001", "Base URL、API Key、模型名称与提示词均不能为空");
        }
        if (isAnthropicAppsGateway(trimmedBase)) {
            return postAnthropicMessages(trimmedBase, trimmedKey, trimmedModel, trimmedPrompt);
        }
        return postOpenAiChatCompletions(trimmedBase, trimmedKey, trimmedModel, trimmedPrompt);
    }

    private static boolean isAnthropicAppsGateway(String baseUrl) {
        return baseUrl.toLowerCase().contains("/apps/anthropic");
    }

    private static String resolveAnthropicMessagesUrl(String baseUrl) {
        String t = baseUrl.replaceAll("/+$", "");
        if (t.matches("(?i).*/v1/messages")) {
            return t;
        }
        if (t.endsWith("/chat/completions")) {
            t = t.substring(0, t.length() - "/chat/completions".length());
        }
        if (t.matches("(?i).*/v1$")) {
            t = t.substring(0, t.length() - "/v1".length());
        }
        return t + "/v1/messages";
    }

    private static String resolveOpenAiChatCompletionsUrl(String baseUrl) {
        String t = baseUrl.replaceAll("/+$", "");
        if (t.endsWith("/chat/completions")) {
            return t;
        }
        if (t.endsWith("/v1")) {
            return t + "/chat/completions";
        }
        return t + "/chat/completions";
    }

    private String postAnthropicMessages(String baseUrl, String apiKey, String modelName, String userPrompt) {
        String url = resolveAnthropicMessagesUrl(baseUrl);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey.startsWith("sk-")) {
            headers.setBearerAuth(apiKey);
        } else {
            headers.set("x-api-key", apiKey);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("max_tokens", 1024);
        body.put("stream", false);
        body.put("messages", List.of(Map.of("role", "user", "content", userPrompt)));
        body.put("thinking", Map.of("type", "disabled"));
        return exchangeForText(url, headers, body, true);
    }

    private String postOpenAiChatCompletions(String baseUrl, String apiKey, String modelName, String userPrompt) {
        String url = resolveOpenAiChatCompletionsUrl(baseUrl);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("stream", false);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", userPrompt));
        body.put("messages", messages);
        return exchangeForText(url, headers, body, false);
    }

    private String exchangeForText(String url, HttpHeaders headers, Map<String, Object> body, boolean anthropic) {
        try {
            String json =
                    restClient
                            .post()
                            .uri(url)
                            .headers(h -> h.addAll(headers))
                            .body(body)
                            .retrieve()
                            .body(String.class);
            return parseAssistantText(json, anthropic);
        } catch (RestClientException e) {
            throw new DomainException("BUS-5020", "调用大模型网关失败: " + e.getMessage());
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            throw new DomainException("BUS-5020", "解析大模型响应失败: " + e.getMessage());
        }
    }

    private String parseAssistantText(String json, boolean anthropic) throws Exception {
        if (json == null || json.isBlank()) {
            throw new DomainException("BUS-5020", "大模型返回空响应");
        }
        JsonNode root = objectMapper.readTree(json);
        if (root.has("error")) {
            JsonNode err = root.get("error");
            String msg = err.path("message").asText(err.toString());
            throw new DomainException("BUS-5020", "大模型返回错误: " + msg);
        }
        if (anthropic) {
            StringBuilder sb = new StringBuilder();
            JsonNode content = root.path("content");
            if (content.isArray()) {
                for (JsonNode block : content) {
                    if (block.has("text")) {
                        sb.append(block.path("text").asText(""));
                    }
                }
            }
            String text = sb.toString().trim();
            if (text.isEmpty()) {
                throw new DomainException("BUS-5020", "大模型响应中未解析到 assistant 文本");
            }
            return text;
        }
        JsonNode textNode = root.path("choices").path(0).path("message").path("content");
        if (textNode.isMissingNode() || textNode.isNull()) {
            throw new DomainException("BUS-5020", "大模型响应中未找到 choices[0].message.content");
        }
        String text = textNode.asText("").trim();
        if (text.isEmpty()) {
            throw new DomainException("BUS-5020", "大模型未返回有效文本");
        }
        return text;
    }
}
