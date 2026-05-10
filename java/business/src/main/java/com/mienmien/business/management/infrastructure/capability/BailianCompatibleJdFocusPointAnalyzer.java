package com.mienmien.business.management.infrastructure.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mienmien.business.management.application.capability.JdFocusPointAnalyzer;
import com.mienmien.business.management.infrastructure.config.BusinessJdAiProperties;
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
 * 通过百炼 <strong>OpenAI 兼容</strong>接口调用文本模型，用于 JD 要点提炼。
 * <p>默认请求地址由配置 {@code mienmien.business.jd-ai.api-url} 指定，一般为
 * {@code https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions}，
 * 请求头使用 {@code Authorization: Bearer &lt;API Key&gt;}，与控制台文档一致。
 * <p>若未配置 {@code api-key}，则返回占位说明，不发起外呼。
 */
@Component
public class BailianCompatibleJdFocusPointAnalyzer implements JdFocusPointAnalyzer {
    private static final String PLACEHOLDER =
            "（JD 要点分析：未配置服务端百炼密钥。请在环境变量或配置中设置 mienmien.business.jd-ai.api-key，"
                    + "并确认 api-url 为 compatible-mode 的 chat/completions 地址后重试。）";

    private final BusinessJdAiProperties props;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public BailianCompatibleJdFocusPointAnalyzer(
            BusinessJdAiProperties props, RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.props = props;
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String analyzeFocusPoints(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }
        if (props.apiKey().isBlank() || props.apiUrl().isBlank()) {
            return PLACEHOLDER;
        }
        String userContent =
                """
                你是人力资源与招聘专家。请阅读以下岗位描述（JD），用简洁的中文分条列出关键信息要点（职责、技能要求、经验、薪资福利亮点等），每条一句，总条数不超过 12 条。不要重复输出 JD 全文。

                JD 原文：
                ---
                %s
                ---
                """
                        .formatted(rawText.trim());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.model());
        body.put("stream", false);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", userContent));
        body.put("messages", messages);
        try {
            String json =
                    restClient
                            .post()
                            .uri(props.apiUrl())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey())
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body(body)
                            .retrieve()
                            .body(String.class);
            return extractAssistantText(json);
        } catch (RestClientException e) {
            return "（调用百炼失败：" + e.getMessage() + "）";
        } catch (Exception e) {
            return "（解析百炼响应失败：" + e.getMessage() + "）";
        }
    }

    private String extractAssistantText(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return "（百炼返回空响应）";
        }
        JsonNode root = objectMapper.readTree(json);
        if (root.has("error")) {
            JsonNode err = root.get("error");
            String msg = err.path("message").asText(err.toString());
            return "（百炼返回错误：" + msg + "）";
        }
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.isNull()) {
            return "（百炼响应中未找到 choices[0].message.content）";
        }
        String text = content.asText("").trim();
        return text.isEmpty() ? "（百炼未返回有效文本）" : text;
    }
}
