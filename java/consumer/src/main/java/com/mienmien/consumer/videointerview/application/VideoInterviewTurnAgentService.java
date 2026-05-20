package com.mienmien.consumer.videointerview.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mienmien.consumer.videointerview.config.VideoInterviewProperties;
import com.mienmien.consumer.videointerview.infrastructure.ai.DashscopeCompatibleChatClient;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 单轮作答后的 Agent：多维度评价、标准答案、是否结束、下一题提示（供流式出题参考）。
 */
@Service
public class VideoInterviewTurnAgentService {

    private final DashscopeCompatibleChatClient chatClient;
    private final VideoInterviewProperties properties;
    private final ObjectMapper objectMapper;

    public VideoInterviewTurnAgentService(
            DashscopeCompatibleChatClient chatClient,
            VideoInterviewProperties properties,
            ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public TurnAgentOutcome runAfterAnswer(
            String stylePromptSnapshot,
            String interviewerStyleKey,
            String resumeSnapshotJson,
            String jobSnapshotJson,
            String conversationDigest,
            String currentQuestion,
            String userAnswer,
            int turnIndex,
            int maxTurnIndexSoFar) {
        if (!chatClient.hasApiKey()) {
            return offlineFallback(turnIndex);
        }
        String sys =
                """
                        你是面试评估与出题助手。只输出一个 JSON 对象，不要 Markdown，不要解释。
                        字段要求（全部必填；字符串可为空）：
                        evaluation：object，必须含 schema_version=2、dimensions（数组，每项 name/score（0 到 100 的整数，百分制）/comment 中文）、overall_summary、strengths、risks（均为中文短文本）。
                        standard_answer：中文，条理清晰的标准答案要点，避免与候选人回答逐字重复。
                        should_end_interview：boolean，是否应结束整场面试。
                        end_reason：字符串，结束原因（中文极短），若不结束可为空串。
                        next_question_hint：字符串，若不结束则为下一题的方向提示（中文）；若 should_end_interview 为 true 则必须为空串。
                        next_question_hint 约束：须引导「与前几问不重复」的提问方向——可以是全新考点，也可以基于候选人刚答内容做深挖，但必须换一种问法/切口，不得复述摘要里已出现过的原问句或仅做同义改写。
                        追问与题量：若已接近题量上限应倾向结束或 wrap；不得人身攻击。
                        【风格与角色】下文【风格与角色综合快照】含面试官风格与本轮角色考察说明；评价维度与下一题方向须与之对齐。""";
        String user =
                "【风格 key】\n"
                        + (interviewerStyleKey == null ? "" : interviewerStyleKey)
                        + "\n【风格与角色综合快照】\n"
                        + (stylePromptSnapshot == null ? "" : stylePromptSnapshot)
                        + "\n【简历 JSON】\n"
                        + (resumeSnapshotJson == null ? "{}" : resumeSnapshotJson)
                        + "\n【岗位 JSON】\n"
                        + (jobSnapshotJson == null ? "{}" : jobSnapshotJson)
                        + "\n【对话摘要】\n"
                        + (conversationDigest == null ? "" : conversationDigest)
                        + "\n【当前题】\n"
                        + (currentQuestion == null ? "" : currentQuestion)
                        + "\n【候选人作答】\n"
                        + (userAnswer == null ? "" : userAnswer)
                        + "\n【当前轮次 turn_index】\n"
                        + turnIndex
                        + "\n【题量硬上限（轮）】\n"
                        + properties.maxQuestionsPerSession()
                        + "\n【已出现过的最大题号】\n"
                        + maxTurnIndexSoFar
                        + "\n【出题不重复约束】\n"
                        + "阅读【对话摘要】中每一轮「问」的正文：下一题方向提示须避免与这些已问句高度雷同；若深挖，须指向候选人回答中的具体事实并提升追问层次（背景→细节→权衡→结果等），而非重复同一笼统问题。";
        Optional<String> raw =
                chatClient.complete(
                        properties.dashscopeBaseUrl(),
                        properties.orchestratorModel(),
                        sys,
                        user,
                        2048);
        if (raw.isEmpty()) {
            return offlineFallback(turnIndex);
        }
        try {
            String s = extractJsonObject(raw.get());
            JsonNode n = objectMapper.readTree(s);
            JsonNode ev = n.path("evaluation");
            final String evaluationJson;
            if (ev.isObject()) {
                evaluationJson = objectMapper.writeValueAsString(ev);
            } else {
                ObjectNode fb = objectMapper.createObjectNode();
                fb.put("schema_version", 2);
                fb.put("overall_summary", ev.isMissingNode() ? "（无评价对象）" : ev.asText(""));
                fb.putArray("dimensions");
                evaluationJson = objectMapper.writeValueAsString(fb);
            }
            String standard = n.path("standard_answer").asText("");
            boolean end = n.path("should_end_interview").asBoolean(false);
            String endReason = n.path("end_reason").asText("");
            String hint = n.path("next_question_hint").asText("");
            if (end) {
                hint = "";
            }
            return new TurnAgentOutcome(evaluationJson, standard, end, endReason, hint, raw.get());
        } catch (Exception e) {
            return offlineFallback(turnIndex);
        }
    }

    private TurnAgentOutcome offlineFallback(int turnIndex) {
        try {
            ObjectNode ev = objectMapper.createObjectNode();
            ev.put("schema_version", 2);
            ev.put("overall_summary", "未配置百炼 API Key（spring.ai.dashscope.api-key）或模型解析失败，暂无法生成结构化评价。");
            ev.putArray("dimensions");
            String ej = objectMapper.writeValueAsString(ev);
            boolean end = turnIndex >= properties.maxQuestionsPerSession();
            return new TurnAgentOutcome(
                    ej,
                    "（离线）请结合岗位与简历，用 STAR 法则组织答案。",
                    end,
                    end ? "max_questions" : "",
                    end ? "" : "下一问请换角度：或深挖候选人上一答中的可验证细节，或切换到简历/岗位中尚未充分考察的新维度；勿复述已问过的问题句。",
                    "");
        } catch (Exception e) {
            return new TurnAgentOutcome("{}", "", false, "", "", "");
        }
    }

    private static String extractJsonObject(String text) {
        int i = text.indexOf('{');
        int j = text.lastIndexOf('}');
        if (i >= 0 && j > i) {
            return text.substring(i, j + 1);
        }
        return text;
    }

    public record TurnAgentOutcome(
            String evaluationJson,
            String standardAnswer,
            boolean shouldEndInterview,
            String endReason,
            String nextQuestionHint,
            String rawModelText) {
    }
}
