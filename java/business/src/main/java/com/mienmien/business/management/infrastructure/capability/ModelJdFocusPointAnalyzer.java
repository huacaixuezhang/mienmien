package com.mienmien.business.management.infrastructure.capability;

import com.mienmien.business.management.application.capability.JdFocusPointAnalyzer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ModelJdFocusPointAnalyzer implements JdFocusPointAnalyzer {
    private static final Pattern CONTENT_PATTERN = Pattern.compile("\"content\"\\s*:\\s*\"([^\"]+)\"");
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Value("${mienmien.business.jd-ai.api-url:https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions}")
    private String apiUrl;
    @Value("${mienmien.business.jd-ai.api-key:}")
    private String apiKey;
    @Value("${mienmien.business.jd-ai.model:qwen-plus}")
    private String model;

    @Override
    public String analyze(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }
        String fromModel = callModel(rawText);
        return (fromModel == null || fromModel.isBlank()) ? fallback(rawText) : fromModel;
    }

    private String callModel(String rawText) {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        try {
            String payload = "{"
                    + "\"model\":\"" + safe(model) + "\","
                    + "\"messages\":["
                    + "{\"role\":\"system\",\"content\":\"你是JD拆解助手。仅输出逗号分隔的关键考点词，不要解释。\"},"
                    + "{\"role\":\"user\",\"content\":\"请拆解这段JD并给出8个以内关键考点：" + safe(rawText) + "\"}"
                    + "]"
                    + "}";
            HttpRequest req = HttpRequest.newBuilder(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(12))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                return "";
            }
            Matcher m = CONTENT_PATTERN.matcher(resp.body());
            if (!m.find()) {
                return "";
            }
            return m.group(1).replace("\\n", ",").replace("，", ",").trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String fallback(String rawText) {
        String text = rawText.toLowerCase(Locale.ROOT);
        Set<String> tags = new LinkedHashSet<>();
        if (text.contains("java")) tags.add("Java");
        if (text.contains("spring")) tags.add("Spring");
        if (text.contains("mysql")) tags.add("MySQL");
        if (text.contains("redis")) tags.add("Redis");
        if (text.contains("mq") || text.contains("消息")) tags.add("消息队列");
        if (text.contains("分布式")) tags.add("分布式");
        if (text.contains("并发")) tags.add("高并发");
        if (text.contains("jvm")) tags.add("JVM");
        if (text.contains("索引")) tags.add("索引优化");
        if (tags.isEmpty()) tags.add("业务理解");
        return String.join(",", tags);
    }

    private String safe(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
