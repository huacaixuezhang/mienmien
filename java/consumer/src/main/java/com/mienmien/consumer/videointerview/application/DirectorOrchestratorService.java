package com.mienmien.consumer.videointerview.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mienmien.consumer.videointerview.config.VideoInterviewProperties;
import com.mienmien.consumer.videointerview.infrastructure.ai.DashscopeCompatibleChatClient;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 面试「导演」：由<strong>单次</strong>大模型调用完成「是否收束当前回答 + 下一步动作 + 面试官话术」，
 * 不再在服务端用启发式/关键词做判停与二次防抖。
 */
@Service
public class DirectorOrchestratorService {

    private final DashscopeCompatibleChatClient chatClient;
    private final VideoInterviewProperties properties;
    private final ObjectMapper objectMapper;

    public DirectorOrchestratorService(
            DashscopeCompatibleChatClient chatClient,
            VideoInterviewProperties properties,
            ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 用户本轮提交：由模型统一判断收束与否并决定面试官下一步。
     *
     * @param interviewerStyleKey 所选风格 key（内置或自定义 id），与 B 端选择一致，供模型对齐人设
     * @param confirmedUserTranscript 本轮已确认转写（如客户端累计 final）
     * @param auxiliaryDetectionContext 其它检测侧原始信号（partial、VAD 等），可为空；模型须一并纳入推理
     */
    public TurnModelDecision decideAfterUserTurn(
            String stylePromptSnapshot,
            String interviewerStyleKey,
            String resumeSnapshotJson,
            String jobSnapshotJson,
            String conversationTranscriptTail,
            String lastInterviewerLine,
            String activeTopic,
            int questionsSoFar,
            int followUpsSinceNewQuestion,
            String confirmedUserTranscript,
            String auxiliaryDetectionContext) {
        String utter = confirmedUserTranscript == null ? "" : confirmedUserTranscript.trim();
        String aux = auxiliaryDetectionContext == null ? "" : auxiliaryDetectionContext.trim();
        String sk = interviewerStyleKey == null ? "" : interviewerStyleKey.trim();
        int fu = Math.max(0, followUpsSinceNewQuestion);
        if (!chatClient.hasApiKey()) {
            return offlineFallback(utter, questionsSoFar);
        }
        String sys =
                """
                        你是整场「语音模拟面试」的唯一决策大脑（对标真实面试官串行节奏）。只输出一个 JSON 对象，不要 Markdown，不要解释。
                        流程必须串行：AI 提问/反馈 → 候选人回答 → 你判定与决定下一步；不要假设并行环节。
                        你必须综合：面试官风格、简历与岗位快照、完整对话上下文、【当前轮已确认转写】以及【本轮检测到的全部辅助原始信号】，判断候选人本轮是否告一段落、下一步动作与面试官口语话术。
                        辅助信号中可能含 partial、VAD 等：须纳入推理并自行分配权重。

                        【风格与角色综合约束】用户消息中的【风格与角色综合快照】同时包含：①面试官风格（语气、节奏、话术边界）；②本轮各席位「角色代号」对应的面试内容、侧重点与评估提示。二者与【风格 key】共同约束整场提问：须同时遵守，不得只执行其一；若风格与角色情境在考察角度上可互补，应自然融合到同一口吻中，避免割裂成两次采访。
                        【风格 key】仅作标签对齐，不替代快照正文；若 key 与快照语义冲突，以快照正文为准。

                        JSON 字段（全部必填，字符串可为空）：
                        user_turn_complete：boolean。候选人本轮回答是否已告一段落；若明显未完、空洞需追问，则为 false。
                        user_turn_confidence：0 到 1 的小数。
                        user_turn_reason：极短中文，说明依据。
                        next_action：必须是 follow_up | new_question | answer_candidate_question | small_talk | wrap_up 之一。
                        interviewer_utterance：本轮要对候选人说的中文口语。user_turn_complete 为 false 时须简短（追问半句、提示补全），勿抛全新大题。
                        should_end_interview：boolean。
                        end_reason：字符串，可空。
                        internal_notes：字符串，可空（给系统用）。

                        追问规则：自上一道「大题」以来若已累计追问次数 ≥ 2，则禁止再输出 next_action=follow_up，必须改为 new_question 或 wrap_up 等；追问应针对当前回答缺口，一语中的。
                        其它规则：题量与节奏合理；不要人身攻击；不要自称 AI。""";
        String auxBlock =
                aux.isEmpty()
                        ? "（本轮未附带额外检测信号，或均为空。）"
                        : aux;
        String user =
                "【所选面试官风格 key】\n"
                        + (sk.isEmpty() ? "（未指定，按风格与角色综合快照默认理解）" : sk)
                        + "\n【风格与角色综合快照】\n"
                        + (stylePromptSnapshot == null ? "" : stylePromptSnapshot)
                        + "\n【简历 JSON】\n"
                        + (resumeSnapshotJson == null ? "{}" : resumeSnapshotJson)
                        + "\n【岗位 JSON】\n"
                        + (jobSnapshotJson == null ? "{}" : jobSnapshotJson)
                        + "\n【最近对话（含角色，截断前保留语义）】\n"
                        + (conversationTranscriptTail == null ? "" : conversationTranscriptTail)
                        + "\n【面试官上一轮话术】\n"
                        + (lastInterviewerLine == null ? "" : lastInterviewerLine)
                        + "\n【当前系统 active_topic】\n"
                        + (activeTopic == null ? "" : activeTopic)
                        + "\n【已出题轮数】\n"
                        + questionsSoFar
                        + "\n【自上一道大题以来已累计追问次数（服务端计数，用于追问上限）】\n"
                        + fu
                        + "\n【系统会话题量硬上限（轮，供你把握节奏；接近时应倾向 wrap_up）】\n"
                        + properties.maxQuestionsPerSession()
                        + "\n【当前轮候选人转写（已确认部分，请据此与辅助信号综合判断）】\n"
                        + utter
                        + "\n【本轮检测到的全部辅助原始信号（partial / VAD 等，请一并纳入推理）】\n"
                        + auxBlock;
        Optional<String> raw =
                chatClient.complete(
                        properties.dashscopeBaseUrl(),
                        properties.orchestratorModel(),
                        sys,
                        user,
                        1536);
        if (raw.isPresent()) {
            try {
                return parseTurnDecision(raw.get());
            } catch (Exception e) {
                /* fall through */
            }
        }
        return modelParseFallback(utter, questionsSoFar);
    }

    /**
     * 方案 B：仅生成「衔接口语」供当场反馈；服务端判停与下一题仍以 {@link VideoInterviewTurnAgentService} 为准。
     * 只解析 JSON 字段 {@code interviewer_utterance}；无 Key 或失败时返回空串。
     */
    public String bridgingUtteranceAfterAnswer(
            String stylePromptSnapshot,
            String interviewerStyleKey,
            String resumeSnapshotJson,
            String jobSnapshotJson,
            String conversationDigestTail,
            String currentQuestion,
            String confirmedUserAnswer,
            int questionsSoFar) {
        if (!chatClient.hasApiKey()) {
            return "";
        }
        String sk = interviewerStyleKey == null ? "" : interviewerStyleKey.trim();
        String sys =
                """
                        你是语音模拟面试中的面试官，只输出一个 JSON 对象，不要 Markdown，不要解释。
                        字段：interviewer_utterance（字符串，必填）。值为你要对候选人说的中文口语，一至两句：承接上一轮回答、简短追问半句或过渡，语气须与下方【风格与角色综合快照】一致。
                        硬性约束：不得输出录用/不录用结论；不得打分或评级；不得写长篇评价（后续系统会单独生成结构化评价，勿抢戏）；不要抛出一道完整的新大题正文；不要自称 AI；不要人身攻击。""";
        String digest = conversationDigestTail == null ? "" : conversationDigestTail.trim();
        String q = currentQuestion == null ? "" : currentQuestion.trim();
        String a = confirmedUserAnswer == null ? "" : confirmedUserAnswer.trim();
        String snap = stylePromptSnapshot == null ? "" : stylePromptSnapshot;
        if (snap.length() > 4000) {
            snap = snap.substring(snap.length() - 4000);
        }
        if (digest.length() > 3500) {
            digest = digest.substring(digest.length() - 3500);
        }
        String user =
                "【所选面试官风格 key】\n"
                        + (sk.isEmpty() ? "（未指定）" : sk)
                        + "\n【风格与角色综合快照】\n"
                        + snap
                        + "\n【简历 JSON】\n"
                        + (resumeSnapshotJson == null ? "{}" : resumeSnapshotJson)
                        + "\n【岗位 JSON】\n"
                        + (jobSnapshotJson == null ? "{}" : jobSnapshotJson)
                        + "\n【最近对话摘要（截断）】\n"
                        + digest
                        + "\n【当前题】\n"
                        + q
                        + "\n【候选人本轮作答】\n"
                        + a
                        + "\n【已出题轮数】\n"
                        + questionsSoFar
                        + "\n【会话题量硬上限（轮）】\n"
                        + properties.maxQuestionsPerSession();
        Optional<String> raw =
                chatClient.complete(
                        properties.dashscopeBaseUrl(),
                        properties.orchestratorModel(),
                        sys,
                        user,
                        256);
        if (raw.isEmpty()) {
            return "";
        }
        try {
            String s = extractJsonObject(raw.get());
            JsonNode n = objectMapper.readTree(s);
            return n.path("interviewer_utterance").asText("").trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    public DirectorDecision opening(
            String interviewerStyleKey, String stylePromptSnapshot, String resumeSnapshotJson, String jobSnapshotJson) {
        String sk = interviewerStyleKey == null ? "" : interviewerStyleKey.trim();
        String user =
                "请生成开场白与第一个问题。只输出 JSON：next_action、interviewer_utterance、should_end_interview、end_reason。\n"
                        + "【所选面试官风格 key】\n"
                        + (sk.isEmpty() ? "（未指定）" : sk)
                        + "\n【风格与角色综合快照】（须同时体现风格语气与本轮角色考察说明中的侧重点）\n"
                        + (stylePromptSnapshot == null ? "" : stylePromptSnapshot)
                        + "\n简历：\n"
                        + (resumeSnapshotJson == null ? "{}" : resumeSnapshotJson)
                        + "\n岗位：\n"
                        + (jobSnapshotJson == null ? "{}" : jobSnapshotJson);
        Optional<String> raw =
                chatClient.complete(
                        properties.dashscopeBaseUrl(),
                        properties.orchestratorModel(),
                        """
                                你是本场语音模拟面试的面试官，只输出 JSON，字段 next_action 固定为 new_question。
                                你必须严格按用户消息中的【风格与角色综合快照】扮演：interviewer_utterance 的称谓、语气、寒暄多少、专业深度须与快照一致，并体现本轮角色情境中的考察侧重；不得使用与快照矛盾的「通用面试官」口吻。""",
                        user,
                        512);
        if (raw.isPresent()) {
            try {
                String s = extractJsonObject(raw.get());
                JsonNode n = objectMapper.readTree(s);
                return new DirectorDecision(
                        "new_question",
                        n.path("interviewer_utterance").asText("你好，欢迎参加本次模拟面试，请先做一个简短自我介绍。"),
                        false,
                        "");
            } catch (Exception ignored) {
                /* fall through */
            }
        }
        return new DirectorDecision(
                "new_question",
                "你好，欢迎参加本次模拟面试。请先做一个约一分钟的自我介绍。",
                false,
                "");
    }

    private TurnModelDecision offlineFallback(String utter, int questionsSoFar) {
        if (utter.isBlank()) {
            return new TurnModelDecision(
                    false,
                    0.3,
                    "offline_no_key_empty",
                    "follow_up",
                    "这边暂时没收到有效语音或文字，请再说一句或改用文字输入。",
                    false,
                    "",
                    "");
        }
        if (questionsSoFar >= properties.maxQuestionsPerSession()) {
            return new TurnModelDecision(
                    true,
                    0.5,
                    "offline_no_key_max_q",
                    "wrap_up",
                    "今天的交流先到这儿，感谢你的时间。",
                    true,
                    "max_questions",
                    "");
        }
        return new TurnModelDecision(
                true,
                0.55,
                "offline_no_key_default",
                "new_question",
                "好的，我们进入下一个问题：请结合你的经历谈谈与岗位最匹配的一项能力。",
                false,
                "",
                "");
    }

    private TurnModelDecision modelParseFallback(String utter, int questionsSoFar) {
        if (questionsSoFar >= properties.maxQuestionsPerSession()) {
            return new TurnModelDecision(
                    true,
                    0.5,
                    "model_parse_fail_max_q",
                    "wrap_up",
                    "今天的交流先到这儿，感谢你的时间。",
                    true,
                    "max_questions",
                    "");
        }
        return new TurnModelDecision(
                true,
                0.5,
                "model_parse_fail",
                "new_question",
                "收到。我们接着往下聊：请举一个最能体现你解决问题能力的例子。",
                false,
                "",
                "");
    }

    private TurnModelDecision parseTurnDecision(String rawText) throws Exception {
        String s = extractJsonObject(rawText);
        JsonNode n = objectMapper.readTree(s);
        return new TurnModelDecision(
                n.path("user_turn_complete").asBoolean(true),
                n.path("user_turn_confidence").asDouble(0.72),
                n.path("user_turn_reason").asText("model"),
                n.path("next_action").asText("new_question"),
                n.path("interviewer_utterance").asText("好的，请继续。"),
                n.path("should_end_interview").asBoolean(false),
                n.path("end_reason").asText(""),
                n.path("internal_notes").asText(""));
    }

    private static String extractJsonObject(String text) {
        int i = text.indexOf('{');
        int j = text.lastIndexOf('}');
        if (i >= 0 && j > i) {
            return text.substring(i, j + 1);
        }
        return text;
    }

    /** 与开场白等旧代码兼容的「仅导演输出」形态 */
    public record DirectorDecision(String nextAction, String interviewerUtterance, boolean shouldEndInterview, String endReason) {
    }

    /**
     * 单次模型调用的完整结果（判停 + 动作合并在同一 JSON）。
     */
    public record TurnModelDecision(
            boolean userTurnComplete,
            double userTurnConfidence,
            String userTurnReason,
            String nextAction,
            String interviewerUtterance,
            boolean shouldEndInterview,
            String endReason,
            String internalNotes) {
    }
}
