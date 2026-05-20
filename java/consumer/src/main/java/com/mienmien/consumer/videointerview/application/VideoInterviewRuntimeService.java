package com.mienmien.consumer.videointerview.application;

import com.alibaba.dashscope.audio.omni.OmniRealtimeCallback;
import com.alibaba.dashscope.audio.omni.OmniRealtimeConversation;
import com.alibaba.dashscope.audio.omni.OmniRealtimeParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.JsonObject;
import com.mienmien.consumer.guidance.domain.support.ShortIdGenerator;
import com.mienmien.consumer.videointerview.config.VideoInterviewProperties;
import com.mienmien.consumer.videointerview.infrastructure.ai.DashscopeCompatibleChatClient;
import com.mienmien.consumer.videointerview.infrastructure.ai.VideoInterviewOmniAsrSupport;
import com.mienmien.consumer.videointerview.infrastructure.persistence.VideoInterviewJdbcStore;
import com.mienmien.consumer.videointerview.infrastructure.persistence.VideoInterviewJdbcStore.VideoInterviewEventRow;
import com.mienmien.consumer.videointerview.infrastructure.persistence.VideoInterviewJdbcStore.VideoInterviewSessionRow;
import com.mienmien.consumer.videointerview.infrastructure.persistence.VideoInterviewJdbcStore.VideoInterviewTurnRow;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 语音模拟面试运行时：流式出题、手动录音提交、轮次落库、Agent 四分拆、同轮重答、WS 单活。
 */
@Service
public class VideoInterviewRuntimeService {
    private static final Logger log = LoggerFactory.getLogger(VideoInterviewRuntimeService.class);

    /** 与 web/src/utils/interviewV3.js 保持一致，避免终局 merge 破坏结构化 summary。 */
    private static final String SUMMARY_PREFIX_V3 = "MM_INTERVIEW_V3::";

    private static final String SUMMARY_PREFIX_V2 = "MM_INTERVIEW_V2::";

    private static final int CLOSING_SNAPSHOT_TAIL_CHARS = 7000;

    private final VideoInterviewJdbcStore store;
    private final DashscopeCompatibleChatClient chatClient;
    private final VideoInterviewTurnAgentService turnAgent;
    private final DirectorOrchestratorService directorOrchestrator;
    private final VideoInterviewProperties properties;
    private final ShortIdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final String dashscopeApiKey;

    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MutableSession> live = new ConcurrentHashMap<>();

    /** 终局总评 LLM 调用与落库在虚拟线程执行，避免长时间占用 WS 线程；与「session_closing」状态配合。 */
    private final ExecutorService closingExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /** Omni 实时 ASR 建连与 endSession 在虚拟线程执行，避免阻塞 WS 入站线程。 */
    private final ExecutorService omniExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public VideoInterviewRuntimeService(
            VideoInterviewJdbcStore store,
            DashscopeCompatibleChatClient chatClient,
            VideoInterviewTurnAgentService turnAgent,
            DirectorOrchestratorService directorOrchestrator,
            VideoInterviewProperties properties,
            ShortIdGenerator idGenerator,
            ObjectMapper objectMapper,
            @Value("${spring.ai.dashscope.api-key:}") String dashscopeApiKey) {
        this.store = store;
        this.chatClient = chatClient;
        this.turnAgent = turnAgent;
        this.directorOrchestrator = directorOrchestrator;
        this.properties = properties;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
        this.dashscopeApiKey = dashscopeApiKey == null ? "" : dashscopeApiKey.trim();
    }

    @PreDestroy
    public void shutdownClosingExecutor() {
        closingExecutor.close();
        omniExecutor.close();
    }

    private Object lock(String sessionId) {
        return locks.computeIfAbsent(sessionId, k -> new Object());
    }

    public void handleConnectionOpen(WebSocketSession ws, VideoInterviewSessionRow row, String userId, long resumeFromSeq)
            throws IOException {
        boolean kickoffFirstStream = false;
        synchronized (lock(row.sessionId())) {
            MutableSession ms = live.computeIfAbsent(row.sessionId(), k -> new MutableSession());
            if (ms.ws != null && ms.ws.isOpen() && ms.ws != ws) {
                try {
                    sendJson(ms.ws, Map.of("type", "displaced", "payload", Map.of("reason", "new_tab")));
                } catch (IOException ignored) {
                    /* ignore */
                }
                try {
                    ms.ws.close(new CloseStatus(4000, "replaced_by_new_client"));
                } catch (IOException ignored) {
                    /* ignore */
                }
            }
            ms.ws = ws;
            for (VideoInterviewEventRow e : store.listEventsAfter(row.sessionId(), resumeFromSeq)) {
                Map<String, Object> p = new HashMap<>();
                p.put("seq", e.seq());
                p.put("eventType", e.type());
                p.put("payload", safeJson(e.payloadJson()));
                sendJson(ws, Map.of("type", "replay", "payload", p));
            }
            if (resumeFromSeq == 0 && "preparing".equals(row.status())) {
                String tid = idGenerator.newId("vt_");
                store.insertTurn(tid, row.sessionId(), 1);
                ms.currentTurnId = tid;
                store.updateSessionStatus(row.sessionId(), "in_progress", null);
                kickoffFirstStream = true;
            }
        }
        if (kickoffFirstStream) {
            VideoInterviewSessionRow useRow = store.loadSession(row.sessionId()).orElse(row);
            MutableSession msSnap;
            WebSocketSession wsSnap;
            synchronized (lock(row.sessionId())) {
                msSnap = live.get(row.sessionId());
                wsSnap = msSnap == null ? null : msSnap.ws;
            }
            if (msSnap != null && wsSnap == ws) {
                startFirstQuestionStream(ws, useRow, msSnap);
            }
        }
    }

    /** 首题：仅流式与落库（首轮回转已在 handleConnectionOpen 的同步块内完成）。须在 WS 锁外执行，避免阻塞入站线程导致 question_delta 无法及时下发。 */
    private void startFirstQuestionStream(WebSocketSession ws, VideoInterviewSessionRow row, MutableSession ms)
            throws IOException {
        String userPrompt =
                "请输出本场模拟面试的**开场白 + 第一个正式问题**的正文（中文口语）。\n"
                        + "【风格 key】\n"
                        + row.styleKey()
                        + "\n【风格与角色综合快照】\n"
                        + row.stylePromptSnapshot()
                        + "\n【简历 JSON】\n"
                        + row.resumeSnapshotJson()
                        + "\n【岗位 JSON】\n"
                        + row.jobSnapshotJson()
                        + "\n要求：只输出要说给候选人听的一段话，不要 JSON、不要标题、不要分点编号以外的 Markdown。";
        String system =
                "你是面试官。严格按【风格与角色综合快照】中的风格语气与本轮角色考察说明输出；称谓与寒暄多少与快照一致。";
        streamQuestion(ws, row, ms, system, userPrompt, true);
    }

    private void streamQuestion(
            WebSocketSession ws,
            VideoInterviewSessionRow row,
            MutableSession ms,
            String system,
            String userPrompt,
            boolean openingQuestion)
            throws IOException {
        ms.fsm = InterviewFsm.QUESTION_STREAMING;
        appendEvent(row.sessionId(), "state", Map.of("state", "question_streaming", "epoch", row.epoch(), "turnId", ms.currentTurnId));
        sendJson(
                ws,
                Map.of("type", "state", "payload", Map.of("state", "question_streaming", "epoch", row.epoch(), "turnId", ms.currentTurnId)));
        StringBuilder acc = new StringBuilder();
        int[] deltaSeq = {0};
        String full;
        try {
            full =
                    chatClient.streamComplete(
                            properties.dashscopeBaseUrl(),
                            properties.orchestratorModel(),
                            system,
                            userPrompt,
                            1536,
                            chunk -> {
                                acc.append(chunk);
                                deltaSeq[0]++;
                                try {
                                    sendJson(
                                            ws,
                                            Map.of(
                                                    "type",
                                                    "question_delta",
                                                    "payload",
                                                    Map.of(
                                                            "turnId",
                                                            ms.currentTurnId,
                                                            "seq",
                                                            deltaSeq[0],
                                                            "textChunk",
                                                            chunk)));
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });
        } catch (Exception e) {
            log.warn("streamQuestion failed, fallback non-stream session={}", row.sessionId(), e);
            sendQuestionGenerationNotice(
                    ws,
                    "CON_QUESTION_STREAM_FAIL",
                    "题目流式生成失败（网络或服务异常），正在尝试非流式补全。若题目不合适可稍后再次点击「下一题」重新生成。");
            String fb =
                    openingQuestion
                            ? "欢迎参加本次模拟面试。请先做一个约一分钟的自我介绍，并说明你申请本岗位的动机。"
                            : "上一题我们已了解你的基本情况。下面请你结合简历与岗位，举一个与工作最相关的项目或经历，说明背景、你的职责、遇到的技术难点与结果（口语一段即可）。";
            Optional<String> repaired =
                    chatClient.complete(
                            properties.dashscopeBaseUrl(),
                            properties.orchestratorModel(),
                            system,
                            userPrompt,
                            1024);
            if (repaired.isEmpty() || repaired.get().isBlank()) {
                sendQuestionGenerationNotice(
                        ws,
                        "CON_QUESTION_COMPLETE_EMPTY",
                        "非流式补全仍未返回有效内容，已启用备用题干。请稍后重试或检查百炼模型与网络。");
                full = fb;
            } else {
                full = repaired.get().trim();
            }
        }
        if (full == null || full.isBlank()) {
            full = acc.toString().trim();
        }
        if (full.isBlank()) {
            sendQuestionGenerationNotice(
                    ws,
                    "CON_QUESTION_MODEL_EMPTY",
                    "题目生成结果为空（模型未输出），已启用备用题干。若多次出现请检查 spring.ai.dashscope.api-key、模型名与配额。");
            full =
                    openingQuestion
                            ? "欢迎参加本次模拟面试。请先做一个简短自我介绍。"
                            : "好的，我们进入下一问：请你结合上一题的回答，进一步说明你在专业上最有把握的一个方向，并给出可核查的要点。";
        }
        store.updateTurnQuestionText(ms.currentTurnId, full);
        appendEvent(row.sessionId(), "question_final", Map.of("turnId", ms.currentTurnId, "text", full));
        sendJson(ws, Map.of("type", "question_done", "payload", Map.of("turnId", ms.currentTurnId, "fullText", full)));
        sendJson(ws, Map.of("type", "speak", "payload", Map.of("text", full, "epoch", row.epoch())));
        ms.fsm = InterviewFsm.AWAITING_ANSWER;
        store.updateSessionStatus(row.sessionId(), "awaiting_answer", null);
        appendEvent(row.sessionId(), "state", Map.of("state", "awaiting_answer", "epoch", row.epoch(), "turnId", ms.currentTurnId));
        sendJson(
                ws,
                Map.of("type", "state", "payload", Map.of("state", "awaiting_answer", "epoch", row.epoch(), "turnId", ms.currentTurnId)));
    }

    /** 出题链路降级或失败时通知客户端（与业务类 error 区分，便于前端展示）。 */
    private void sendQuestionGenerationNotice(WebSocketSession ws, String code, String message) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", code);
        payload.put("message", message);
        payload.put("context", "question_generation");
        sendJson(ws, Map.of("type", "error", "payload", payload));
    }

    private Object safeJson(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            return raw;
        }
    }

    public void handleClientJson(WebSocketSession ws, VideoInterviewSessionRow row, String userId, String json)
            throws IOException {
        JsonNode node = objectMapper.readTree(json);
        String type = node.path("type").asText("");
        if ("ping".equals(type)) {
            synchronized (lock(row.sessionId())) {
                MutableSession ms = live.get(row.sessionId());
                if (ms == null || ms.ws == null || ms.ws != ws) {
                    return;
                }
                sendJson(
                        ws,
                        Map.of(
                                "type",
                                "pong",
                                "payload",
                                Map.of(
                                        "t",
                                        System.currentTimeMillis(),
                                        "realtimeAsrEnabled",
                                        useClientOmniStreams())));
            }
            return;
        }
        if ("continue_next".equals(type)) {
            Optional<ContinueNextStreamPlan> plan;
            synchronized (lock(row.sessionId())) {
                MutableSession ms = live.get(row.sessionId());
                if (ms == null || ms.ws == null || ms.ws != ws) {
                    return;
                }
                if (ms.fsm == InterviewFsm.SESSION_CLOSING) {
                    sendJson(ws, Map.of("type", "error", "payload", Map.of("message", "会话正在生成终局总评，请稍候")));
                    return;
                }
                plan = buildContinueNextPlan(ws, row, ms, userId);
            }
            if (plan.isPresent()) {
                MutableSession msSnap;
                synchronized (lock(row.sessionId())) {
                    msSnap = live.get(row.sessionId());
                }
                if (msSnap != null && msSnap.ws == ws) {
                    VideoInterviewSessionRow useRow = store.loadSession(row.sessionId()).orElse(row);
                    streamQuestion(ws, useRow, msSnap, plan.get().system(), plan.get().userPrompt(), false);
                }
            }
            return;
        }
        synchronized (lock(row.sessionId())) {
            MutableSession ms = live.get(row.sessionId());
            if (ms == null || ms.ws == null || ms.ws != ws) {
                return;
            }
            if (ms.fsm == InterviewFsm.SESSION_CLOSING) {
                if ("end_interview".equals(type)) {
                    return;
                }
                sendJson(ws, Map.of("type", "error", "payload", Map.of("message", "会话正在生成终局总评，请稍候")));
                return;
            }
            switch (type) {
                case "record_start" -> onRecordStart(ws, row, ms);
                case "answer_submit" -> onAnswerSubmit(ws, row, ms, userId, node);
                case "finish_session" -> onFinishSession(ws, row, ms, userId, node);
                case "turn_retry_same" -> onTurnRetrySame(ws, row, ms, node);
                case "asr_pcm_base64" -> onAsrPcmBase64(ws, row, ms, node);
                case "asr_realtime_end" -> onAsrRealtimeEnd(ws, row, ms);
                case "end_interview" -> {
                    if (ms.fsm != InterviewFsm.ENDED && ms.fsm != InterviewFsm.SESSION_CLOSING) {
                        beginSessionClosing(ws, row.sessionId(), ms, userId, "user_requested");
                    }
                }
                case "transcript_partial", "transcript_final", "vad_end", "user_turn_commit", "audio_pcm16_base64" ->
                        log.debug("videoInterview.legacy type={} ignored session={}", type, row.sessionId());
                default -> log.debug("videoInterview.unknown type={} session={}", type, row.sessionId());
            }
        }
    }

    private void onRecordStart(WebSocketSession ws, VideoInterviewSessionRow row, MutableSession ms) throws IOException {
        if (ms.fsm != InterviewFsm.AWAITING_ANSWER) {
            sendJson(ws, Map.of("type", "error", "payload", Map.of("message", "当前不可开始录音")));
            return;
        }
        shutdownOmniForAnswer(ms);
        ms.realtimeAsrCommitted.setLength(0);
        ms.omniPendingB64.clear();
        ms.omniReady = !useClientOmniStreams();
        ms.fsm = InterviewFsm.RECORDING;
        appendEvent(row.sessionId(), "record_started", Map.of("turnId", ms.currentTurnId));
        sendJson(ws, Map.of("type", "state", "payload", Map.of("state", "recording", "turnId", ms.currentTurnId)));
        if (useClientOmniStreams()) {
            startOmniRealtimeAsr(ws, row, ms, ms.currentTurnId);
        }
    }

    private void onAnswerSubmit(WebSocketSession ws, VideoInterviewSessionRow row, MutableSession ms, String userId, JsonNode node)
            throws IOException {
        if (ms.fsm != InterviewFsm.RECORDING) {
            sendJson(ws, Map.of("type", "error", "payload", Map.of("message", "请先开始录音再提交作答")));
            return;
        }
        String turnId = node.path("turnId").asText("").trim();
        if (!turnId.equals(ms.currentTurnId)) {
            sendJson(ws, Map.of("type", "error", "payload", Map.of("message", "turnId 不匹配")));
            return;
        }
        shutdownOmniForAnswer(ms);
        String clientText = node.path("text").asText("").trim();
        String serverText = ms.realtimeAsrCommitted.toString().replaceAll("\\s+", " ").trim();
        ms.realtimeAsrCommitted.setLength(0);
        String text = serverText.isBlank() ? clientText : serverText;
        if (text.isBlank()) {
            sendJson(ws, Map.of("type", "error", "payload", Map.of("message", "作答文本不能为空")));
            ms.fsm = InterviewFsm.AWAITING_ANSWER;
            return;
        }
        Optional<VideoInterviewSessionRow> opt = store.loadSession(row.sessionId());
        if (opt.isEmpty() || "ended".equals(opt.get().status())) {
            return;
        }
        VideoInterviewSessionRow current = opt.get();
        if (current.lastEventSeq() >= properties.maxEventsPerSession()) {
            beginSessionClosing(ws, row.sessionId(), ms, userId, "quota_events");
            return;
        }
        ms.fsm = InterviewFsm.AGENT_PROCESSING;
        appendEvent(row.sessionId(), "state", Map.of("state", "agent_processing", "turnId", turnId));
        sendJson(ws, Map.of("type", "state", "payload", Map.of("state", "agent_processing", "turnId", turnId)));
        store.updateTurnAnswerText(turnId, text);
        appendEvent(row.sessionId(), "answer_submitted", Map.of("turnId", turnId, "text", text));

        Optional<VideoInterviewTurnRow> turnOpt = store.loadTurn(turnId);
        int turnIndex = turnOpt.map(VideoInterviewTurnRow::turnIndex).orElse(1);
        int maxIdx = store.maxTurnIndex(row.sessionId());
        String digest = buildConversationDigest(row.sessionId());
        String questionText = turnOpt.map(VideoInterviewTurnRow::questionText).orElse("");

        String bridgingText = "";
        if (properties.bridgingEnabled()) {
            long tBridging0 = System.nanoTime();
            try {
                bridgingText =
                        directorOrchestrator.bridgingUtteranceAfterAnswer(
                                current.stylePromptSnapshot(),
                                current.styleKey(),
                                current.resumeSnapshotJson(),
                                current.jobSnapshotJson(),
                                digest,
                                questionText,
                                text,
                                turnIndex);
            } catch (Exception e) {
                log.debug("videoInterview.bridging failed session={} turnId={}", row.sessionId(), turnId, e);
                bridgingText = "";
            }
            bridgingText = truncateBridgingUtterance(bridgingText, properties.bridgingUtteranceMaxChars());
            log.debug(
                    "videoInterview.bridging session={} turnId={} durationMs={}",
                    row.sessionId(),
                    turnId,
                    (System.nanoTime() - tBridging0) / 1_000_000L);
        }

        long tAgent0 = System.nanoTime();
        VideoInterviewTurnAgentService.TurnAgentOutcome agent =
                turnAgent.runAfterAnswer(
                        current.stylePromptSnapshot(),
                        current.styleKey(),
                        current.resumeSnapshotJson(),
                        current.jobSnapshotJson(),
                        digest,
                        questionText,
                        text,
                        turnIndex,
                        maxIdx);
        log.debug(
                "videoInterview.turnAgent session={} turnId={} durationMs={}",
                row.sessionId(),
                turnId,
                (System.nanoTime() - tAgent0) / 1_000_000L);
        store.updateTurnAgentFields(turnId, bridgingText, agent.evaluationJson(), agent.standardAnswer(), agent.rawModelText());
        ms.lastAgentShouldEnd = agent.shouldEndInterview();
        ms.lastNextQuestionHint = agent.nextQuestionHint() == null ? "" : agent.nextQuestionHint();
        if (maxIdx >= properties.maxQuestionsPerSession()) {
            ms.lastAgentShouldEnd = true;
        }
        Map<String, Object> turnResult = new HashMap<>();
        turnResult.put("turnId", turnId);
        turnResult.put("evaluation", safeJson(agent.evaluationJson()));
        turnResult.put("standardAnswer", agent.standardAnswer());
        turnResult.put("shouldEndInterview", ms.lastAgentShouldEnd);
        turnResult.put("endReason", agent.endReason());
        turnResult.put("nextQuestionHint", agent.nextQuestionHint());
        turnResult.put("bridgingUtterance", bridgingText);
        appendEvent(row.sessionId(), "turn_result", turnResult);
        sendJson(ws, Map.of("type", "turn_result", "payload", turnResult));

        ms.fsm = InterviewFsm.POST_TURN_REVIEW;
        appendEvent(
                row.sessionId(),
                "state",
                Map.of("state", "post_turn_review", "turnId", turnId, "shouldEndInterview", ms.lastAgentShouldEnd));
        sendJson(
                ws,
                Map.of(
                        "type",
                        "state",
                        "payload",
                        Map.of(
                                "state",
                                "post_turn_review",
                                "turnId",
                                turnId,
                                "shouldEndInterview",
                                ms.lastAgentShouldEnd)));
    }

    /**
     * 在已持有会话锁的前提下构造「下一题」流式参数；失败时返回 empty（并已向客户端发送 error）。
     */
    private Optional<ContinueNextStreamPlan> buildContinueNextPlan(
            WebSocketSession ws, VideoInterviewSessionRow row, MutableSession ms, String userId) throws IOException {
        if (ms.fsm != InterviewFsm.POST_TURN_REVIEW) {
            sendJson(ws, Map.of("type", "error", "payload", Map.of("message", "当前不可进入下一题")));
            return Optional.empty();
        }
        if (ms.lastAgentShouldEnd) {
            sendJson(ws, Map.of("type", "error", "payload", Map.of("message", "本轮已建议结束，请点击页眉「结束面试并生成总评」")));
            return Optional.empty();
        }
        if (store.maxTurnIndex(row.sessionId()) >= properties.maxQuestionsPerSession()) {
            beginSessionClosing(ws, row.sessionId(), ms, userId, "max_questions");
            return Optional.empty();
        }
        int nextIdx = store.maxTurnIndex(row.sessionId()) + 1;
        String tid = idGenerator.newId("vt_");
        store.insertTurn(tid, row.sessionId(), nextIdx);
        ms.currentTurnId = tid;
        String digest = buildConversationDigest(row.sessionId(), ms.currentTurnId);
        String userPrompt =
                "【已进行轮次摘要】\n"
                        + digest
                        + "\n【下一题方向提示】\n"
                        + (ms.lastNextQuestionHint == null ? "" : ms.lastNextQuestionHint)
                        + "\n【出题不重复与深挖规则】\n"
                        + "你正在生成第 "
                        + nextIdx
                        + " 题（口语一段，候选人将直接听到）。\n"
                        + "1）与摘要中每一轮「问」的正文禁止高度雷同：不得逐句复述或仅做同义改写的「同一问」；不得回到与第 1 题同类的「请再做一遍笼统自我介绍」式问法。\n"
                        + "2）允许两类合法路径：A）全新考察点（简历/岗位中尚未覆盖或覆盖不足的能力项）；B）就候选人已答内容「深挖一层」——必须换切入角度或追问颗粒度（例如从陈述→具体案例→技术取舍→协作与结果验证），而不是把上一问再说一遍。\n"
                        + "3）若【下一题方向提示】非空，应优先与之对齐，但仍须满足 1）2）。\n"
                        + "请输出**下一道正式面试问题**的正文（中文口语）；严格按【风格与角色综合快照】中的风格与角色考察侧重。\n"
                        + "【风格 key】\n"
                        + row.styleKey()
                        + "\n【风格与角色综合快照】\n"
                        + row.stylePromptSnapshot()
                        + "\n【简历 JSON】\n"
                        + row.resumeSnapshotJson()
                        + "\n【岗位 JSON】\n"
                        + row.jobSnapshotJson()
                        + "\n只输出要说给候选人听的一段话，不要 JSON、不要标题。";
        String system =
                "你是资深面试官。本条输出必须是「新问句」：要么换新的考察维度，要么在已答事实上往深处多问一层；禁止与摘要里任意一轮「问」在措辞与考察意图上高度重复。严格按【风格与角色综合快照】的语气与考察侧重。";
        return Optional.of(new ContinueNextStreamPlan(userPrompt, system));
    }

    private record ContinueNextStreamPlan(String userPrompt, String system) {}

    private void onFinishSession(WebSocketSession ws, VideoInterviewSessionRow row, MutableSession ms, String userId, JsonNode node)
            throws IOException {
        if (ms.fsm != InterviewFsm.POST_TURN_REVIEW) {
            sendJson(ws, Map.of("type", "error", "payload", Map.of("message", "当前不可结束面试")));
            return;
        }
        if (!ms.lastAgentShouldEnd) {
            sendJson(ws, Map.of("type", "error", "payload", Map.of("message", "请先完成本轮或点击下一题")));
            return;
        }
        beginSessionClosing(ws, row.sessionId(), ms, userId, node.path("reason").asText("agent_should_end"));
    }

    private void onTurnRetrySame(WebSocketSession ws, VideoInterviewSessionRow row, MutableSession ms, JsonNode node)
            throws IOException {
        if (ms.fsm != InterviewFsm.POST_TURN_REVIEW) {
            sendJson(ws, Map.of("type", "error", "payload", Map.of("message", "仅在本轮回顾阶段可重答")));
            return;
        }
        String turnId = node.path("turnId").asText("").trim();
        if (!turnId.equals(ms.currentTurnId)) {
            sendJson(ws, Map.of("type", "error", "payload", Map.of("message", "turnId 不匹配")));
            return;
        }
        store.clearTurnForSameRoundRetry(turnId);
        appendEvent(row.sessionId(), "turn_retry_cleared", Map.of("turnId", turnId));
        shutdownOmniForAnswer(ms);
        ms.realtimeAsrCommitted.setLength(0);
        ms.fsm = InterviewFsm.AWAITING_ANSWER;
        sendJson(ws, Map.of("type", "state", "payload", Map.of("state", "awaiting_answer", "turnId", turnId)));
    }

    private String buildConversationDigest(String sessionId) {
        return buildConversationDigest(sessionId, null);
    }

    /**
     * @param excludeTurnId 若不为空，则跳过该轮（用于「下一题」已插入空占位行、摘要中不应出现未出题的第 N 轮）。
     */
    private String buildConversationDigest(String sessionId, String excludeTurnId) {
        List<VideoInterviewTurnRow> turns = store.listTurns(sessionId);
        StringBuilder sb = new StringBuilder();
        for (VideoInterviewTurnRow t : turns) {
            if (excludeTurnId != null && excludeTurnId.equals(t.turnId())) {
                continue;
            }
            sb.append("[第").append(t.turnIndex()).append("轮] turnId=").append(t.turnId()).append('\n');
            sb.append("问：").append(t.questionText() == null ? "" : t.questionText()).append('\n');
            sb.append("答：").append(t.answerText() == null ? "" : t.answerText()).append("\n\n");
        }
        String s = sb.toString().trim();
        return tail(s, 12000);
    }

    private static String tail(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(s.length() - max);
    }

    /**
     * 进入终局：先切 {@link InterviewFsm#SESSION_CLOSING}、落库 {@code session_closing} 并推送 state，再在后台线程生成总评；
     * 期间拒绝业务类 WS 消息（判停后仅在 {@code post_turn_review} 内可同轮重答，点此路径后不可再重答）。
     */
    private void beginSessionClosing(WebSocketSession ws, String sessionId, MutableSession ms, String userId, String reason)
            throws IOException {
        synchronized (lock(sessionId)) {
            if (ms.fsm == InterviewFsm.ENDED) {
                return;
            }
            if (ms.fsm == InterviewFsm.SESSION_CLOSING) {
                return;
            }
            shutdownOmniForAnswer(ms);
            ms.fsm = InterviewFsm.SESSION_CLOSING;
            store.updateSessionStatus(sessionId, "session_closing", null);
            appendEvent(sessionId, "state", Map.of("state", "session_closing", "reason", reason));
            sendJson(
                    ws,
                    Map.of("type", "state", "payload", Map.of("state", "session_closing", "reason", reason, "sessionId", sessionId)));
        }
        closingExecutor.execute(() -> completeSessionClosing(sessionId, reason));
    }

    private void completeSessionClosing(String sessionId, String reason) {
        try {
            Optional<VideoInterviewSessionRow> opt = store.loadSession(sessionId);
            if (opt.isEmpty()) {
                synchronized (lock(sessionId)) {
                    clearClosingPlaceholder(sessionId);
                }
                return;
            }
            VideoInterviewSessionRow row = opt.get();
            if ("ended".equals(row.status())) {
                synchronized (lock(sessionId)) {
                    clearClosingPlaceholder(sessionId);
                }
                return;
            }
            if (!"session_closing".equals(row.status())) {
                log.warn("completeSessionClosing unexpected status sessionId={} status={}", sessionId, row.status());
                synchronized (lock(sessionId)) {
                    clearClosingPlaceholder(sessionId);
                }
                return;
            }
            String digest = buildConversationDigest(sessionId);
            Optional<ObjectNode> closingJson = tryBuildClosingAnalysisJson(row, digest);
            String evaluation;
            ObjectNode interviewConclusion;
            if (closingJson.isPresent()) {
                ObjectNode p = closingJson.get();
                evaluation = p.path("evaluation").asText("").trim();
                interviewConclusion = normalizeInterviewConclusionFromLlm(p);
                if (evaluation.isBlank()) {
                    evaluation = interviewConclusion.path("comment").asText("").trim();
                }
                if (evaluation.isBlank()) {
                    evaluation = buildOverallEvaluation(row, digest);
                }
                if (interviewConclusion.path("comment").asText("").isBlank()) {
                    interviewConclusion.put("comment", evaluation);
                }
            } else {
                evaluation = buildOverallEvaluation(row, digest);
                interviewConclusion = normalizeInterviewConclusionFromLlm(null);
                interviewConclusion.put("comment", evaluation);
            }
            appendEvent(sessionId, "evaluation", Map.of("text", evaluation, "reason", reason));
            mergeInterviewRecordSummary(row.businessRecordId(), sessionId, evaluation, row.roundIndex(), interviewConclusion);
            mergeVoiceTurnQuestionsIntoBusinessV3Summary(row.businessRecordId(), sessionId, row.roundIndex());
            store.updateSessionStatus(sessionId, "ended", Instant.now());
            appendEvent(sessionId, "state", Map.of("state", "ended", "epoch", row.epoch()));

            Map<String, Object> p = new HashMap<>();
            p.put("reason", reason);
            p.put("evaluation", evaluation);
            p.put("sessionId", sessionId);

            synchronized (lock(sessionId)) {
                MutableSession ms = live.get(sessionId);
                if (ms != null && ms.fsm == InterviewFsm.SESSION_CLOSING) {
                    try {
                        sendJson(ms.ws, Map.of("type", "ended", "payload", p));
                    } catch (IOException e) {
                        log.warn("send ended failed sessionId={}", sessionId, e);
                    }
                    ms.fsm = InterviewFsm.ENDED;
                    live.remove(sessionId);
                    locks.remove(sessionId);
                }
            }
        } catch (Exception e) {
            log.error("completeSessionClosing failed sessionId={}", sessionId, e);
            synchronized (lock(sessionId)) {
                try {
                    store.updateSessionStatus(sessionId, "ended", Instant.now());
                    appendEvent(sessionId, "evaluation", Map.of("text", "", "reason", reason, "error", "closing_failed"));
                    long epoch = store.loadSession(sessionId).map(VideoInterviewSessionRow::epoch).orElse(0L);
                    appendEvent(sessionId, "state", Map.of("state", "ended", "epoch", epoch));
                } catch (Exception ex) {
                    log.warn("completeSessionClosing recovery persist failed sessionId={}", sessionId, ex);
                }
                MutableSession ms = live.get(sessionId);
                if (ms != null && ms.fsm == InterviewFsm.SESSION_CLOSING) {
                    try {
                        sendJson(
                                ms.ws,
                                Map.of(
                                        "type",
                                        "ended",
                                        "payload",
                                        Map.of("reason", reason, "evaluation", "终局总评生成失败，请稍后在业务侧查看会话与事件。", "sessionId", sessionId)));
                    } catch (IOException ignored) {
                        /* ignore */
                    }
                    ms.fsm = InterviewFsm.ENDED;
                    live.remove(sessionId);
                    locks.remove(sessionId);
                }
            }
        }
    }

    /** 异步终局流程异常或会话已结束时，尽量释放 WS 占位。 */
    private void clearClosingPlaceholder(String sessionId) {
        MutableSession ms = live.get(sessionId);
        if (ms != null && ms.fsm == InterviewFsm.SESSION_CLOSING) {
            ms.fsm = InterviewFsm.ENDED;
            live.remove(sessionId);
            locks.remove(sessionId);
        }
    }

    private static final String CLOSING_SYSTEM_ALIGNMENT =
            "总评须严格以用户消息中「各轮问答摘要」的事实为依据，不得编造摘要未出现的内容。\n"
                    + "语气、考察维度与评价侧重须与「风格与角色综合快照」及「本轮面试记录语境」（若有）一致，并与轮次类型/业务面相呼应；勿硬套 HR、peer、LD 等固定标签，以快照与轮次说明为准。\n";

    private static String truncateBridgingUtterance(String raw, int maxChars) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return "";
        }
        if (maxChars <= 0) {
            return "";
        }
        if (s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars) + "…";
    }

    /** 终局两条 LLM 路径共用：风格 key、快照 tail、V3 轮次语境（若有）。 */
    private void appendClosingStyleAndRoundContext(StringBuilder userBody, VideoInterviewSessionRow row) {
        String sk = row.styleKey() == null ? "" : row.styleKey().trim();
        userBody.append("【所选面试官风格 key】\n").append(sk.isEmpty() ? "（未指定）" : sk).append('\n');
        String snap = row.stylePromptSnapshot() == null ? "" : row.stylePromptSnapshot();
        userBody.append("【风格与角色综合快照】\n").append(tail(snap, CLOSING_SNAPSHOT_TAIL_CHARS)).append('\n');
        String roundCtx = buildV3RoundContextBlock(row.businessRecordId(), row.roundIndex());
        if (!roundCtx.isBlank()) {
            userBody.append("【本轮面试记录语境】\n").append(roundCtx).append('\n');
        }
    }

    private String buildV3RoundContextBlock(String recordId, int roundIndex) {
        if (recordId == null || recordId.isBlank()) {
            return "";
        }
        try {
            String raw = store.loadInterviewSummary(recordId);
            if (raw == null) {
                return "";
            }
            String t = raw.trim();
            if (!t.startsWith(SUMMARY_PREFIX_V3)) {
                return "";
            }
            JsonNode root = objectMapper.readTree(t.substring(SUMMARY_PREFIX_V3.length()));
            JsonNode rounds = root.get("rounds");
            if (rounds == null || !rounds.isArray() || roundIndex < 0 || roundIndex >= rounds.size()) {
                return "";
            }
            JsonNode r = rounds.get(roundIndex);
            if (r == null || !r.isObject()) {
                return "";
            }
            StringBuilder b = new StringBuilder();
            b.append("roundTitle=").append(r.path("roundTitle").asText("")).append('\n');
            b.append("category=").append(r.path("category").asText("")).append('\n');
            JsonNode ivs = r.get("interviewers");
            if (ivs != null && ivs.isArray() && ivs.size() > 0) {
                b.append("interviewers=");
                for (int i = 0; i < ivs.size(); i++) {
                    JsonNode x = ivs.get(i);
                    if (i > 0) {
                        b.append("；");
                    }
                    b.append(x.path("role").asText("")).append("/").append(x.path("name").asText(""));
                }
                b.append('\n');
            }
            return b.toString().trim();
        } catch (Exception e) {
            log.debug("buildV3RoundContextBlock failed recordId={}", recordId, e);
            return "";
        }
    }

    private String buildOverallEvaluation(VideoInterviewSessionRow row, String digest) {
        String t = digest == null ? "" : digest.trim();
        if (t.isBlank()) {
            return "本次会话较短，暂无足够内容生成详细评价。";
        }
        String digestPart = tail(t, 8000);
        StringBuilder user = new StringBuilder();
        appendClosingStyleAndRoundContext(user, row);
        user.append("【各轮问答摘要】\n").append(digestPart);
        String system =
                "你是面试评估助手。"
                        + CLOSING_SYSTEM_ALIGNMENT
                        + "只输出 280 字以内的中文总体评价正文，不要标题与 Markdown。";
        Optional<String> ev =
                chatClient.complete(
                        properties.dashscopeBaseUrl(),
                        properties.orchestratorModel(),
                        system,
                        user.toString(),
                        640);
        return ev.orElseGet(
                () ->
                        "【语音模拟面试】会话 "
                                + row.sessionId()
                                + " 已结束。在 application.yml 配置 spring.ai.dashscope.api-key 后可生成大模型总体评价。");
    }

    private static String stripOptionalJsonFence(String s) {
        String x = s == null ? "" : s.trim();
        if (x.startsWith("```")) {
            int nl = x.indexOf('\n');
            if (nl > 0) {
                x = x.substring(nl + 1);
            }
            int end = x.lastIndexOf("```");
            if (end >= 0) {
                x = x.substring(0, end);
            }
        }
        return x.trim();
    }

    /**
     * 终局结构化结论：与前端 {@code summary.rounds[roundIndex].interviewConclusion} 对齐；失败时返回 empty，由调用方回退纯文本总评。
     */
    private Optional<ObjectNode> tryBuildClosingAnalysisJson(VideoInterviewSessionRow row, String digest) {
        String t = digest == null ? "" : digest.trim();
        if (t.isBlank()) {
            return Optional.empty();
        }
        if (dashscopeApiKey == null || dashscopeApiKey.isBlank()) {
            return Optional.empty();
        }
        String system =
                "你是面试评估助手。"
                        + CLOSING_SYSTEM_ALIGNMENT
                        + "请仅输出一个 JSON 对象，不要使用 Markdown 代码围栏，不要输出任何 JSON 以外的文字。\n"
                        + "字段要求：\n"
                        + "evaluation：字符串，280 字以内的中文总体评价；\n"
                        + "resultAssessment：字符串，必须是「通过」「未通过」「待评估」之一；\n"
                        + "overallScore：0 到 100 的整数，表示你在通盘阅读各轮问答后的综合打分（后续会与逐题得分加权合成最终 overallScore）；\n"
                        + "questionWeights：数组，每项为对象，必须含 videoTurnId（字符串，须与摘要中各轮「turnId=」后的标识完全一致）与 weight（0 到 1 的小数）；\n"
                        + "仅覆盖本场会话摘要中出现的问答轮次；所有项 weight 之和必须等于 1（允许误差 ±0.02，否则服务端会按比例归一）；\n"
                        + "comment：字符串，面试评语；\n"
                        + "candidatePortrait：字符串，对候选人能力与特质的简要画像；\n"
                        + "nextRoundAdvice：字符串，当 nextRoundStatus 为 yes 或 pending 时可填写下轮/后续建议，为 no 时写空字符串；\n"
                        + "nextRoundStatus：字符串，必须是 no、yes、pending 之一（分别表示无下一轮、有或计划下一轮、待定）；\n"
                        + "若同时输出 hasNextRound 布尔值，以 nextRoundStatus 为准。";
        StringBuilder user = new StringBuilder();
        appendClosingStyleAndRoundContext(user, row);
        user.append("【各轮问答摘要】\n").append(tail(t, 8000));
        Optional<String> rawOpt =
                chatClient.complete(
                        properties.dashscopeBaseUrl(),
                        properties.orchestratorModel(),
                        system,
                        user.toString(),
                        1200);
        if (rawOpt.isEmpty()) {
            return Optional.empty();
        }
        String raw = stripOptionalJsonFence(rawOpt.get());
        if (raw.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode n = objectMapper.readTree(raw);
            if (n.isObject()) {
                return Optional.of((ObjectNode) n);
            }
        } catch (Exception e) {
            log.debug("tryBuildClosingAnalysisJson parse failed sessionId={}", row.sessionId(), e);
        }
        return Optional.empty();
    }

    private ObjectNode normalizeInterviewConclusionFromLlm(JsonNode raw) {
        ObjectNode d = objectMapper.createObjectNode();
        String ra = raw == null ? "" : raw.path("resultAssessment").asText("").trim();
        if ("通过".equals(ra)) {
            d.put("resultAssessment", "通过");
        } else if ("未通过".equals(ra) || "拒绝".equals(ra)) {
            d.put("resultAssessment", "未通过");
        } else {
            d.put("resultAssessment", "待评估");
        }
        int sc = raw == null ? 0 : raw.path("overallScore").asInt(0);
        d.put("overallScore", Math.min(100, Math.max(0, sc)));
        d.set("questionWeights", normalizeQuestionWeightsArrayFromLlm(raw));
        d.put("comment", raw == null ? "" : raw.path("comment").asText("").trim());
        d.put("candidatePortrait", raw == null ? "" : raw.path("candidatePortrait").asText("").trim());
        String nrs = raw == null ? "" : raw.path("nextRoundStatus").asText("").trim().toLowerCase();
        if ("yes".equals(nrs) || "pending".equals(nrs) || "no".equals(nrs)) {
            d.put("nextRoundStatus", nrs);
        } else {
            boolean hn = raw != null && raw.path("hasNextRound").asBoolean(false);
            d.put("nextRoundStatus", hn ? "yes" : "no");
        }
        String nrsFinal = d.get("nextRoundStatus").asText("");
        boolean isYes = "yes".equals(nrsFinal);
        boolean keepNextRoundAdvice = isYes || "pending".equals(nrsFinal);
        d.put("hasNextRound", isYes);
        if (keepNextRoundAdvice) {
            d.put("nextRoundAdvice", raw == null ? "" : raw.path("nextRoundAdvice").asText("").trim());
        } else {
            d.put("nextRoundAdvice", "");
        }
        return d;
    }

    /** 解析终局 LLM 的 questionWeights，与 Web normalizeQuestionWeightEntries 对齐。 */
    private ArrayNode normalizeQuestionWeightsArrayFromLlm(JsonNode raw) {
        ArrayNode out = objectMapper.createArrayNode();
        if (raw == null || !raw.isObject()) {
            return out;
        }
        JsonNode arr = raw.get("questionWeights");
        if (arr == null || !arr.isArray() || arr.isEmpty()) {
            return out;
        }
        double sum = 0;
        for (JsonNode item : arr) {
            if (item == null || !item.isObject()) {
                continue;
            }
            String tid = item.path("videoTurnId").asText("").trim();
            if (tid.isEmpty()) {
                tid = item.path("turnId").asText("").trim();
            }
            double w = item.path("weight").asDouble(Double.NaN);
            if (tid.isEmpty() || !Double.isFinite(w) || w < 0) {
                continue;
            }
            ObjectNode o = objectMapper.createObjectNode();
            o.put("videoTurnId", tid);
            o.put("weight", w);
            out.add(o);
            sum += w;
        }
        if (out.isEmpty()) {
            return out;
        }
        if (sum > 1e-6 && Math.abs(sum - 1.0) > 0.02) {
            for (JsonNode x : out) {
                ObjectNode on = (ObjectNode) x;
                double ow = on.path("weight").asDouble();
                on.put("weight", ow / sum);
            }
        }
        return out;
    }

    private static String assessmentToResultUi(String assessment) {
        if ("通过".equals(assessment)) {
            return "通过";
        }
        if ("未通过".equals(assessment) || "拒绝".equals(assessment)) {
            return "未通过";
        }
        return "待评估";
    }

    private static String assessmentToDbResult(String assessment) {
        if ("通过".equals(assessment)) {
            return "passed";
        }
        if ("未通过".equals(assessment) || "拒绝".equals(assessment)) {
            return "failed";
        }
        return "pending";
    }

    /** 与前端 {@code resultUiToApi} 对齐：列表/筛选用的稳定编码。 */
    private static String resultUiToResultCode(String resultUi) {
        if ("通过".equals(resultUi)) {
            return "passed";
        }
        if ("拒绝".equals(resultUi) || "未通过".equals(resultUi)) {
            return "failed";
        }
        return "pending";
    }

    private ObjectNode defaultInterviewConclusionNode() {
        ObjectNode ic = objectMapper.createObjectNode();
        ic.put("resultAssessment", "待评估");
        ic.put("overallScore", 0);
        ic.put("comment", "");
        ic.put("candidatePortrait", "");
        ic.put("nextRoundAdvice", "");
        ic.put("nextRoundStatus", "no");
        ic.put("hasNextRound", false);
        ic.set("questionWeights", objectMapper.createArrayNode());
        return ic;
    }

    /**
     * 语音回合合并写入 summary 前，保证该轮具备与 Web 持久化一致的结构（含 {@code interviewConclusion} 全字段、
     * {@code roundIndex}、{@code resultCode}），避免旧数据或占位轮缺字段。
     */
    private void ensureV3RoundPersistShape(ObjectNode round, int roundIndex) {
        round.put("roundIndex", roundIndex);
        String resultUi = round.path("resultUi").asText("").trim();
        if (resultUi.isEmpty()) {
            resultUi = "待评估";
            round.put("resultUi", resultUi);
        }
        round.put("resultCode", resultUiToResultCode(resultUi));
        JsonNode icNode = round.get("interviewConclusion");
        if (icNode == null || !icNode.isObject()) {
            round.set("interviewConclusion", defaultInterviewConclusionNode());
            return;
        }
        ObjectNode ic = (ObjectNode) icNode;
        ObjectNode def = defaultInterviewConclusionNode();
        def.fieldNames()
                .forEachRemaining(
                        f -> {
                            if (!ic.has(f)) {
                                ic.set(f, def.get(f).deepCopy());
                            }
                        });
        String nrs = ic.path("nextRoundStatus").asText("").trim().toLowerCase();
        if (!"yes".equals(nrs) && !"pending".equals(nrs) && !"no".equals(nrs)) {
            boolean hn = ic.path("hasNextRound").asBoolean(false);
            ic.put("nextRoundStatus", hn ? "yes" : "no");
        }
        nrs = ic.path("nextRoundStatus").asText("").trim().toLowerCase();
        boolean isYes = "yes".equals(nrs);
        boolean keepAdvice = isYes || "pending".equals(nrs);
        ic.put("hasNextRound", isYes);
        if (!keepAdvice) {
            ic.put("nextRoundAdvice", "");
        }
    }

    private void ensureV3RootHasRoundsArray(ObjectNode root) {
        if (!root.has("rounds") || !root.get("rounds").isArray()) {
            root.set("rounds", objectMapper.createArrayNode());
        }
    }

    /** 与前端 {@code ensureRoundsCoverVideoRoundIndex} 对齐：语音落库 roundIndex 可能大于当前 rounds 长度。 */
    private void padV3RoundsToIncludeRoundIndex(ArrayNode rounds, int roundIndex) {
        if (rounds == null || roundIndex < 0) {
            return;
        }
        while (rounds.size() <= roundIndex) {
            rounds.add(buildBlankV3RoundNode(rounds.size()));
        }
    }

    private ObjectNode buildBlankV3RoundNode(int zeroBasedIndex) {
        ObjectNode r = objectMapper.createObjectNode();
        r.put("id", idGenerator.newId("r_"));
        r.put("roundTitle", "第" + (zeroBasedIndex + 1) + "轮面试");
        r.put("timeText", "");
        r.put("locationMode", "线上");
        r.put("category", "技术面");
        r.put("interviewerStyleKey", "builtin_general");
        ArrayNode iv = objectMapper.createArrayNode();
        ObjectNode hr = objectMapper.createObjectNode();
        hr.put("role", "HR");
        hr.put("name", "");
        iv.add(hr);
        r.set("interviewers", iv);
        r.put("resultUi", "待评估");
        r.put("resultComment", "");
        r.put("roundIndex", zeroBasedIndex);
        r.put("resultCode", resultUiToResultCode("待评估"));
        r.set("interviewConclusion", defaultInterviewConclusionNode());
        r.set("questions", objectMapper.createArrayNode());
        return r;
    }

    /** 将终局结构化结论写入 {@code rounds[roundIndex]}，与前端每轮 {@code interviewConclusion} 对齐。 */
    private void applyConclusionToRoundV3(ObjectNode root, int roundIndex, ObjectNode conclusion) {
        if (root == null || conclusion == null) {
            return;
        }
        JsonNode rounds = root.get("rounds");
        if (rounds == null || !rounds.isArray() || roundIndex < 0 || roundIndex >= rounds.size()) {
            return;
        }
        JsonNode rn = rounds.get(roundIndex);
        if (!rn.isObject()) {
            return;
        }
        ObjectNode round = (ObjectNode) rn;
        round.set("interviewConclusion", conclusion);
        String ui = assessmentToResultUi(conclusion.path("resultAssessment").asText(""));
        round.put("resultUi", ui);
        round.put("resultCode", resultUiToResultCode(ui));
        round.put("roundIndex", roundIndex);
    }

    private void applyMetaVideoInterviewAndConclusionV3(
            ObjectNode root, ObjectNode vi, ObjectNode interviewConclusionOrNull) {
        if (vi == null) {
            return;
        }
        ensureV3RootHasRoundsArray(root);
        int sessionRoundIndex = vi.path("roundIndex").asInt(0);
        padV3RoundsToIncludeRoundIndex((ArrayNode) root.get("rounds"), sessionRoundIndex);
        ObjectNode meta =
                root.has("meta") && root.get("meta").isObject()
                        ? (ObjectNode) root.get("meta")
                        : root.putObject("meta");
        meta.remove("interviewConclusion");
        meta.set("videoInterviewMeta", vi);
        if (interviewConclusionOrNull != null) {
            applyConclusionToRoundV3(root, sessionRoundIndex, interviewConclusionOrNull);
        }
    }

    private void patchBusinessResultFromConclusion(String recordId, ObjectNode interviewConclusion) {
        if (recordId == null || recordId.isBlank() || interviewConclusion == null) {
            return;
        }
        store.patchInterviewRecordResult(
                recordId, assessmentToDbResult(interviewConclusion.path("resultAssessment").asText("")));
    }

    private void mergeInterviewRecordSummary(
            String recordId,
            String sessionId,
            String evaluation,
            int roundIndex,
            ObjectNode interviewConclusion) {
        try {
            String existing = store.loadInterviewSummary(recordId);
            String trimmed = existing == null ? "" : existing.trim();
            ObjectNode vi = objectMapper.createObjectNode();
            vi.put("sessionId", sessionId);
            vi.put("evaluation", evaluation == null ? "" : evaluation);
            vi.put("mergedAt", Instant.now().toString());
            vi.put("roundIndex", roundIndex);

            if (trimmed.startsWith(SUMMARY_PREFIX_V3)) {
                ObjectNode root =
                        (ObjectNode) objectMapper.readTree(trimmed.substring(SUMMARY_PREFIX_V3.length()));
                applyMetaVideoInterviewAndConclusionV3(root, vi, interviewConclusion);
                store.updateInterviewSummary(recordId, SUMMARY_PREFIX_V3 + objectMapper.writeValueAsString(root));
                patchBusinessResultFromConclusion(recordId, interviewConclusion);
                return;
            }
            if (trimmed.startsWith(SUMMARY_PREFIX_V2)) {
                ObjectNode root =
                        (ObjectNode) objectMapper.readTree(trimmed.substring(SUMMARY_PREFIX_V2.length()));
                root.set("videoInterviewMeta", vi);
                store.updateInterviewSummary(recordId, SUMMARY_PREFIX_V2 + objectMapper.writeValueAsString(root));
                return;
            }
            if (!trimmed.isBlank()) {
                try {
                    JsonNode maybe = objectMapper.readTree(trimmed);
                    if (maybe.isObject()) {
                        ObjectNode obj = (ObjectNode) maybe;
                        if (obj.has("legacySummary") && obj.get("legacySummary").isTextual()) {
                            String leg = obj.get("legacySummary").asText("").trim();
                            if (leg.startsWith(SUMMARY_PREFIX_V3)) {
                                ObjectNode root =
                                        (ObjectNode) objectMapper.readTree(leg.substring(SUMMARY_PREFIX_V3.length()));
                                applyMetaVideoInterviewAndConclusionV3(root, vi, interviewConclusion);
                                store.updateInterviewSummary(
                                        recordId, SUMMARY_PREFIX_V3 + objectMapper.writeValueAsString(root));
                                patchBusinessResultFromConclusion(recordId, interviewConclusion);
                                return;
                            }
                        }
                    }
                } catch (Exception ignored) {
                    /* fall through */
                }
            }

            ObjectNode merged;
            try {
                merged = (ObjectNode) objectMapper.readTree(trimmed.isBlank() ? "{}" : trimmed);
            } catch (Exception e) {
                merged = objectMapper.createObjectNode();
                merged.put("legacySummary", existing == null ? "" : existing);
            }
            merged.set("videoInterviewMeta", vi);
            store.updateInterviewSummary(recordId, objectMapper.writeValueAsString(merged));
        } catch (Exception e) {
            log.warn("mergeInterviewRecordSummary failed recordId={}", recordId, e);
        }
    }

    /**
     * 将本场语音逐轮（Consumer mm_video_interview_turn）写入业务面试 summary 的 V3
     * {@code rounds[roundIndex].questions}，与前端 {@code source=video_turn} 结构对齐；仅处理 {@code MM_INTERVIEW_V3::}。
     */
    private void mergeVoiceTurnQuestionsIntoBusinessV3Summary(
            String businessRecordId, String sessionId, int roundIndex) {
        try {
            String existing = store.loadInterviewSummary(businessRecordId);
            if (existing == null || existing.isBlank() || !existing.trim().startsWith(SUMMARY_PREFIX_V3)) {
                return;
            }
            String trimmed = existing.trim();
            ObjectNode root =
                    (ObjectNode) objectMapper.readTree(trimmed.substring(SUMMARY_PREFIX_V3.length()));
            if (!root.has("rounds") || !root.get("rounds").isArray()) {
                log.warn("mergeVoiceTurnQuestions skip: no rounds array recordId={}", businessRecordId);
                return;
            }
            ArrayNode rounds = (ArrayNode) root.get("rounds");
            if (roundIndex < 0) {
                log.warn(
                        "mergeVoiceTurnQuestions skip: negative roundIndex recordId={} idx={}",
                        businessRecordId,
                        roundIndex);
                return;
            }
            padV3RoundsToIncludeRoundIndex(rounds, roundIndex);
            JsonNode roundNode = rounds.get(roundIndex);
            if (!roundNode.isObject()) {
                return;
            }
            ObjectNode round = (ObjectNode) roundNode;
            ensureV3RoundPersistShape(round, roundIndex);
            ArrayNode questions;
            JsonNode qn = round.get("questions");
            if (qn != null && qn.isArray()) {
                questions = (ArrayNode) qn;
            } else {
                questions = objectMapper.createArrayNode();
                round.set("questions", questions);
            }
            int sessionOrdinal = computeVideoSessionOrdinal(questions, sessionId);
            for (int i = questions.size() - 1; i >= 0; i--) {
                JsonNode q = questions.get(i);
                if (!q.isObject()) {
                    continue;
                }
                if ("video_turn".equals(q.path("source").asText(""))
                        && sessionId.equals(q.path("videoSessionId").asText(""))) {
                    questions.remove(i);
                }
            }
            List<VideoInterviewTurnRow> toAdd = new ArrayList<>();
            for (VideoInterviewTurnRow t : store.listTurns(sessionId)) {
                if (turnRowHasInterviewContent(t)) {
                    toAdd.add(t);
                }
            }
            for (int ord = 0; ord < toAdd.size(); ord++) {
                questions.add(buildVoiceTurnQuestionNode(sessionId, toAdd.get(ord), ord + 1, sessionOrdinal));
            }
            relabelMultiSessionVoiceTurnQuestions(questions);
            syncRoundOverallScoreFromVoiceQuestions(round, questions);
            store.updateInterviewSummary(
                    businessRecordId, SUMMARY_PREFIX_V3 + objectMapper.writeValueAsString(root));
        } catch (Exception e) {
            log.warn(
                    "mergeVoiceTurnQuestionsIntoBusinessV3Summary failed recordId={} sessionId={}",
                    businessRecordId,
                    sessionId,
                    e);
        }
    }

    private static boolean turnRowHasInterviewContent(VideoInterviewTurnRow t) {
        String qt = nullToEmpty(t.questionText()).trim();
        String at = nullToEmpty(t.answerText()).trim();
        String sa = nullToEmpty(t.standardAnswer()).trim();
        String ev = nullToEmpty(t.evaluationJson()).trim();
        return !qt.isEmpty() || !at.isEmpty() || !sa.isEmpty() || !ev.isEmpty();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * 与 Web {@code buildVideoSessionOrdinalMap} 一致：按题目列表中首次出现的 {@code videoSessionId} 顺序得到本场「第几场」。
     */
    private static int computeVideoSessionOrdinal(ArrayNode questions, String sessionId) {
        String cur = sessionId == null ? "" : sessionId.trim();
        LinkedHashSet<String> order = new LinkedHashSet<>();
        if (questions != null) {
            for (JsonNode qn : questions) {
                if (!qn.isObject()) {
                    continue;
                }
                if (!"video_turn".equals(qn.path("source").asText(""))) {
                    continue;
                }
                String sid = qn.path("videoSessionId").asText("").trim();
                if (!sid.isEmpty()) {
                    order.add(sid);
                }
            }
        }
        if (!cur.isEmpty()) {
            order.add(cur);
        }
        int idx = 1;
        for (String sid : order) {
            if (sid.equals(cur)) {
                return idx;
            }
            idx++;
        }
        return 1;
    }

    /**
     * 与 Web {@code enrichVoiceTurnLabelsWhenMultiSession} 一致：多场时按 {@code videoSessionId} 首次出现顺序编号并
     * 写「第 n 场｜语音第 m 题」；单场（≤1 个会话 id）写「语音第 k 题」，覆盖旧版「语音 Qn」等。
     */
    private static void relabelMultiSessionVoiceTurnQuestions(ArrayNode questions) {
        if (questions == null || questions.isEmpty()) {
            return;
        }
        LinkedHashSet<String> sessionOrder = new LinkedHashSet<>();
        for (JsonNode qn : questions) {
            if (!qn.isObject()) {
                continue;
            }
            if (!"video_turn".equals(qn.path("source").asText(""))) {
                continue;
            }
            String sid = qn.path("videoSessionId").asText("").trim();
            if (!sid.isEmpty()) {
                sessionOrder.add(sid);
            }
        }
        if (sessionOrder.size() <= 1) {
            int seq = 0;
            for (JsonNode qn : questions) {
                if (!qn.isObject()) {
                    continue;
                }
                ObjectNode q = (ObjectNode) qn;
                if (!"video_turn".equals(q.path("source").asText(""))) {
                    continue;
                }
                seq++;
                q.put("videoSessionOrdinal", 1);
                q.put("label", voiceQuestionLabelSimple(seq));
            }
            return;
        }
        Map<String, Integer> sidToOrdinal = new HashMap<>();
        int o = 1;
        for (String sid : sessionOrder) {
            sidToOrdinal.put(sid, o++);
        }
        Map<String, Integer> perSidCount = new HashMap<>();
        for (JsonNode qn : questions) {
            if (!qn.isObject()) {
                continue;
            }
            ObjectNode q = (ObjectNode) qn;
            if (!"video_turn".equals(q.path("source").asText(""))) {
                continue;
            }
            String sid = q.path("videoSessionId").asText("").trim();
            if (sid.isEmpty()) {
                continue;
            }
            Integer ord = sidToOrdinal.get(sid);
            if (ord == null) {
                continue;
            }
            int prev = perSidCount.getOrDefault(sid, 0);
            int displayNum = prev + 1;
            perSidCount.put(sid, displayNum);
            q.put("videoSessionOrdinal", ord);
            q.put("label", voiceQuestionLabel(displayNum, ord));
        }
    }

    /**
     * 与 Web {@code normalizeQuestionWeightEntries} 一致：从 {@code interviewConclusion.questionWeights} 数组解析并归一化。
     */
    private ArrayNode normalizeQuestionWeightEntriesArray(JsonNode rawArr) {
        ArrayNode out = objectMapper.createArrayNode();
        if (rawArr == null || !rawArr.isArray() || rawArr.isEmpty()) {
            return out;
        }
        double sum = 0;
        for (JsonNode item : rawArr) {
            if (item == null || !item.isObject()) {
                continue;
            }
            String tid = item.path("videoTurnId").asText("").trim();
            if (tid.isEmpty()) {
                tid = item.path("turnId").asText("").trim();
            }
            double w = item.path("weight").asDouble(Double.NaN);
            if (tid.isEmpty() || !Double.isFinite(w) || w < 0) {
                continue;
            }
            ObjectNode o = objectMapper.createObjectNode();
            o.put("videoTurnId", tid);
            o.put("weight", w);
            out.add(o);
            sum += w;
        }
        if (out.isEmpty()) {
            return out;
        }
        if (sum > 1e-9 && Math.abs(sum - 1.0) > 0.02) {
            for (JsonNode x : out) {
                ObjectNode on = (ObjectNode) x;
                double ow = on.path("weight").asDouble();
                on.put("weight", ow / sum);
            }
        }
        return out;
    }

    /** 与 Web {@code buildResolvedVoiceQuestionWeightMap} 对齐。 */
    private Map<String, Double> buildResolvedVoiceQuestionWeightMap(ObjectNode ic, List<ObjectNode> includedQs) {
        ArrayNode norm = normalizeQuestionWeightEntriesArray(ic.get("questionWeights"));
        List<String> tids = new ArrayList<>();
        for (ObjectNode q : includedQs) {
            String tid = q.path("videoTurnId").asText("").trim();
            if (!tid.isEmpty()) {
                tids.add(tid);
            }
        }
        if (tids.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> fromAgent = new HashMap<>();
        for (JsonNode x : norm) {
            if (!x.isObject()) {
                continue;
            }
            String tid = x.path("videoTurnId").asText("").trim();
            double w = x.path("weight").asDouble(Double.NaN);
            if (!tid.isEmpty() && Double.isFinite(w) && w >= 0) {
                fromAgent.put(tid, w);
            }
        }
        double sumKnown = 0;
        int missing = 0;
        for (String tid : tids) {
            Double w = fromAgent.get(tid);
            if (w != null && Double.isFinite(w) && w >= 0) {
                sumKnown += w;
            } else {
                missing++;
            }
        }
        double fill = missing > 0 ? Math.max(0, 1 - sumKnown) / missing : 0;
        Map<String, Double> out = new HashMap<>();
        double total = 0;
        for (String tid : tids) {
            Double w0 = fromAgent.get(tid);
            double w = (w0 != null && Double.isFinite(w0) && w0 >= 0) ? w0 : fill;
            out.put(tid, w);
            total += w;
        }
        if (total <= 1e-9) {
            double u = 1.0 / tids.size();
            Map<String, Double> equal = new HashMap<>();
            for (String tid : tids) {
                equal.put(tid, u);
            }
            return equal;
        }
        if (Math.abs(total - 1.0) > 0.02) {
            for (String tid : tids) {
                out.put(tid, out.get(tid) / total);
            }
        }
        return out;
    }

    private static boolean includeVoiceScoreForOverall(
            JsonNode q, boolean multiSession, int maxOrdinal, String latestSid) {
        if (!multiSession) {
            return true;
        }
        if (maxOrdinal > 0) {
            int qo = q.path("videoSessionOrdinal").asInt(0);
            String qsid = q.path("videoSessionId").asText("").trim();
            boolean byOrdinal = qo == maxOrdinal;
            boolean byLatestSid = (!(qo > 0)) && !qsid.isEmpty() && qsid.equals(latestSid);
            return byOrdinal || byLatestSid;
        }
        return q.path("videoSessionId").asText("").trim().equals(latestSid);
    }

    /**
     * 与 Web {@code syncInterviewConclusionOverallScoreFromQuestions} 对齐：按 questionWeights 加权语音题得分，并与原
     * overallScore 做 65/35 合成；多场次时仅最新一场内语音题参与。
     */
    private void syncRoundOverallScoreFromVoiceQuestions(ObjectNode round, ArrayNode questions) {
        if (round == null || questions == null || questions.isEmpty()) {
            return;
        }
        LinkedHashSet<String> sessionOrderSids = new LinkedHashSet<>();
        for (JsonNode qn : questions) {
            if (!qn.isObject()) {
                continue;
            }
            if (!"video_turn".equals(qn.path("source").asText(""))) {
                continue;
            }
            String sid = qn.path("videoSessionId").asText("").trim();
            if (!sid.isEmpty()) {
                sessionOrderSids.add(sid);
            }
        }
        List<String> sessionOrderList = new ArrayList<>(sessionOrderSids);
        int maxOrdinal = 0;
        for (JsonNode qn : questions) {
            if (!qn.isObject()) {
                continue;
            }
            if (!"video_turn".equals(qn.path("source").asText(""))) {
                continue;
            }
            int o = qn.path("videoSessionOrdinal").asInt(0);
            if (o > maxOrdinal) {
                maxOrdinal = o;
            }
        }
        boolean multiSession = sessionOrderList.size() > 1;
        String latestSid =
                sessionOrderList.isEmpty() ? "" : sessionOrderList.get(sessionOrderList.size() - 1);

        List<ObjectNode> includedQs = new ArrayList<>();
        for (JsonNode qn : questions) {
            if (!qn.isObject()) {
                continue;
            }
            ObjectNode q = (ObjectNode) qn;
            if (!"video_turn".equals(q.path("source").asText(""))) {
                continue;
            }
            if (!includeVoiceScoreForOverall(q, multiSession, maxOrdinal, latestSid)) {
                q.put("scoreWeight", 0);
                continue;
            }
            int sc = q.path("score").asInt(-1);
            if (sc >= 0 && sc <= 100) {
                includedQs.add(q);
            } else {
                q.put("scoreWeight", 0);
            }
        }
        if (includedQs.isEmpty()) {
            return;
        }
        JsonNode icNode = round.get("interviewConclusion");
        ObjectNode ic;
        if (icNode != null && icNode.isObject()) {
            ic = (ObjectNode) icNode;
        } else {
            ic = defaultInterviewConclusionNode();
            round.set("interviewConclusion", ic);
        }
        double llmOverall = ic.path("overallScore").asDouble(Double.NaN);
        double curClamped = Double.isFinite(llmOverall) ? Math.min(100, Math.max(0, llmOverall)) : 0;
        ic.set("questionWeights", normalizeQuestionWeightEntriesArray(ic.get("questionWeights")));
        Map<String, Double> wMap = buildResolvedVoiceQuestionWeightMap(ic, includedQs);
        double weighted = 0;
        for (ObjectNode q : includedQs) {
            String tid = q.path("videoTurnId").asText("").trim();
            double w = wMap.getOrDefault(tid, 0.0);
            double clampedW = Math.min(1, Math.max(0, w));
            q.put("scoreWeight", clampedW);
            weighted += q.path("score").asInt(0) * clampedW;
        }
        for (JsonNode qn : questions) {
            if (!qn.isObject()) {
                continue;
            }
            ObjectNode q = (ObjectNode) qn;
            if (!"video_turn".equals(q.path("source").asText(""))) {
                continue;
            }
            if (!includedQs.contains(q)) {
                q.put("scoreWeight", 0);
            }
        }
        int blended = (int) Math.round(0.65 * weighted + 0.35 * curClamped);
        ic.put("overallScore", Math.min(100, Math.max(0, blended)));
    }

    /** 单场语音：与 Web {@code formatVoiceTurnQuestionLabelSimple} 一致。 */
    private static String voiceQuestionLabelSimple(int displayNum) {
        int qi = displayNum > 0 ? displayNum : 1;
        return "语音第" + qi + "题";
    }

    /** 多场：与 Web {@code formatVoiceTurnQuestionLabel} 一致。 */
    private static String voiceQuestionLabel(int displayNum, int sessionOrdinal) {
        int bout = sessionOrdinal > 0 ? sessionOrdinal : 1;
        int qi = displayNum > 0 ? displayNum : 1;
        return "第" + bout + "场｜语音第" + qi + "题";
    }

    private ObjectNode buildVoiceTurnQuestionNode(
            String sessionId, VideoInterviewTurnRow t, int displayNum, int sessionOrdinal) {
        ObjectNode q = objectMapper.createObjectNode();
        q.put("id", "vi_" + t.turnId());
        q.put("label", voiceQuestionLabel(displayNum, sessionOrdinal));
        q.put("videoSessionOrdinal", sessionOrdinal > 0 ? sessionOrdinal : 1);
        String qt = nullToEmpty(t.questionText()).trim();
        String title = qt.length() > 120 ? qt.substring(0, 120) : qt;
        if (title.isEmpty()) {
            title = "第 " + displayNum + " 题";
        }
        q.put("title", title);
        q.put("questionRecord", nullToEmpty(t.questionText()));
        q.put("answerRecord", nullToEmpty(t.answerText()));
        q.put("standardAnswer", nullToEmpty(t.standardAnswer()));
        applyTurnEvaluationJson(q, nullToEmpty(t.evaluationJson()));
        q.put("difficulty", 2);
        int scr = extractVoiceTurnScoreFromEvaluationJson(nullToEmpty(t.evaluationJson()));
        q.put("score", Math.min(100, Math.max(0, scr)));
        q.put("source", "video_turn");
        q.put("videoTurnId", t.turnId());
        q.put("videoSessionId", sessionId);
        return q;
    }

    /**
     * 与 Web {@code extractVoiceTurnScoreFromEvaluationJson} 一致：从 Agent evaluation（dimensions 或 overall_score）得到 0–100。
     */
    private int extractVoiceTurnScoreFromEvaluationJson(String evaluationJson) {
        String ev = evaluationJson == null ? "" : evaluationJson.trim();
        if (ev.isEmpty()) {
            return 0;
        }
        try {
            JsonNode j = objectMapper.readTree(ev);
            if (j.has("overall_score")) {
                int v = j.path("overall_score").asInt(-1);
                if (v >= 0 && v <= 100) {
                    return v;
                }
            }
            JsonNode dims = j.get("dimensions");
            if (dims == null || !dims.isArray() || dims.isEmpty()) {
                return 0;
            }
            long sum = 0;
            int c = 0;
            for (JsonNode d : dims) {
                int sc = d.path("score").asInt(-1);
                if (sc >= 0 && sc <= 100) {
                    sum += sc;
                    c++;
                }
            }
            if (c <= 0) {
                return 0;
            }
            return (int) Math.min(100, Math.max(0, Math.round((double) sum / c)));
        } catch (Exception e) {
            return 0;
        }
    }

    private void applyTurnEvaluationJson(ObjectNode q, String evaluationJson) {
        String ev = evaluationJson.trim();
        if (ev.isEmpty()) {
            return;
        }
        try {
            JsonNode j = objectMapper.readTree(ev);
            String pros = pickTextArrayOrField(j, "pros", "strengths");
            String cons = pickTextArrayOrField(j, "cons", "weaknesses", "risks");
            String plan = pickTextArrayOrField(j, "improvementPlan", "suggestions");
            if (!pros.isEmpty()) {
                q.put("pros", pros);
            }
            if (!cons.isEmpty()) {
                q.put("cons", cons);
            }
            if (!plan.isEmpty()) {
                q.put("improvementPlan", plan);
            }
        } catch (Exception e) {
            String tail = ev.length() > 8000 ? ev.substring(0, 8000) : ev;
            q.put("improvementPlan", tail);
        }
    }

    /** 依次尝试字段：文本直接返回；数组则拼接非空项。 */
    private static String pickTextArrayOrField(JsonNode j, String... fieldNames) {
        for (String name : fieldNames) {
            JsonNode n = j.get(name);
            if (n == null || n.isNull()) {
                continue;
            }
            if (n.isTextual()) {
                String t = n.asText("").trim();
                if (!t.isEmpty()) {
                    return t;
                }
            }
            if (n.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode item : n) {
                    String s = item.asText("").trim();
                    if (!s.isEmpty()) {
                        if (sb.length() > 0) {
                            sb.append('；');
                        }
                        sb.append(s);
                    }
                }
                if (sb.length() > 0) {
                    return sb.toString();
                }
            }
        }
        return "";
    }

    private void appendEvent(String sessionId, String type, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            String eid = idGenerator.newId("ve_");
            long seq = store.appendEvent(sessionId, eid, type, json);
            log.debug("videoInterview.event session={} seq={} type={}", sessionId, seq, type);
        } catch (Exception e) {
            log.error("appendEvent failed session={}", sessionId, e);
        }
    }

    private void sendJson(WebSocketSession ws, Object payload) throws IOException {
        if (ws == null || !ws.isOpen()) {
            return;
        }
        ws.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
    }

    private boolean useClientOmniStreams() {
        return properties.realtimeAsrEnabled() && !dashscopeApiKey.isBlank();
    }

    private void startOmniRealtimeAsr(WebSocketSession ws, VideoInterviewSessionRow row, MutableSession ms, String boundTurnId) {
        final String sid = row.sessionId();
        omniExecutor.execute(
                () -> {
                    final OmniRealtimeConversation[] convHolder = new OmniRealtimeConversation[1];
                    OmniRealtimeConversation conv = null;
                    try {
                        OmniRealtimeParam param =
                                VideoInterviewOmniAsrSupport.buildParam(
                                        properties.realtimeAsrModel(),
                                        properties.realtimeAsrWsUrl(),
                                        dashscopeApiKey);
                        OmniRealtimeCallback callback =
                                new OmniRealtimeCallback() {
                                    @Override
                                    public void onEvent(JsonObject message) {
                                        dispatchOmniDownstreamEvent(sid, boundTurnId, convHolder[0], message);
                                    }

                                    @Override
                                    public void onClose(int code, String reason) {
                                        dispatchOmniTransportClosed(sid, boundTurnId, convHolder[0], code, reason);
                                    }
                                };
                        conv = new OmniRealtimeConversation(param, callback);
                        convHolder[0] = conv;
                        conv.connect();
                        conv.updateSession(VideoInterviewOmniAsrSupport.buildConfig());
                        synchronized (lock(sid)) {
                            MutableSession m = live.get(sid);
                            if (m == null
                                    || m.ws != ws
                                    || !boundTurnId.equals(m.currentTurnId)
                                    || m.fsm != InterviewFsm.RECORDING) {
                                shutdownOmniConversationDetached(conv);
                                return;
                            }
                            if (m.omniAsr != null && m.omniAsr != conv) {
                                shutdownOmniConversationDetached(m.omniAsr);
                            }
                            m.omniAsr = conv;
                            m.omniReady = true;
                            flushOmniPending(m);
                        }
                        sendJson(ws, Map.of("type", "asr_ready", "payload", Map.of("turnId", boundTurnId)));
                    } catch (Exception e) {
                        log.warn("Omni ASR connect failed session={}", sid, e);
                        shutdownOmniConversationDetached(conv);
                        synchronized (lock(sid)) {
                            MutableSession m = live.get(sid);
                            if (m != null
                                    && m.ws == ws
                                    && boundTurnId.equals(m.currentTurnId)
                                    && m.fsm == InterviewFsm.RECORDING) {
                                m.omniPendingB64.clear();
                                m.omniReady = true;
                                m.omniAsr = null;
                            }
                        }
                        try {
                            sendJson(
                                    ws,
                                    Map.of(
                                            "type",
                                            "error",
                                            "payload",
                                            Map.of(
                                                    "message",
                                                    "实时语音转写连接失败，请使用页面上的识别结果提交，或检查网络与百炼配置。")));
                        } catch (IOException ignored) {
                            /* ignore */
                        }
                    }
                });
    }

    private void onAsrPcmBase64(WebSocketSession ws, VideoInterviewSessionRow row, MutableSession ms, JsonNode node)
            throws IOException {
        if (ms.fsm != InterviewFsm.RECORDING || !useClientOmniStreams()) {
            return;
        }
        String b64 = node.path("base64").asText("");
        if (b64.isBlank()) {
            return;
        }
        OmniRealtimeConversation c = ms.omniAsr;
        if (ms.omniReady && c != null) {
            try {
                c.appendAudio(b64);
            } catch (Exception e) {
                log.debug("Omni appendAudio failed session={}", row.sessionId(), e);
            }
        } else if (!ms.omniReady) {
            if (ms.omniPendingB64.size() < 2000) {
                ms.omniPendingB64.addLast(b64);
            }
        }
    }

    private void onAsrRealtimeEnd(WebSocketSession ws, VideoInterviewSessionRow row, MutableSession ms) throws IOException {
        if (!useClientOmniStreams()) {
            return;
        }
        OmniRealtimeConversation c = ms.omniAsr;
        if (c == null) {
            sendJson(
                    ws,
                    Map.of(
                            "type",
                            "asr_realtime_closed",
                            "payload",
                            Map.of("turnId", ms.currentTurnId, "code", 0, "reason", "no_active")));
            return;
        }
        ms.omniAsr = null;
        ms.omniReady = false;
        ms.omniPendingB64.clear();
        final WebSocketSession w = ws;
        final String tid = ms.currentTurnId;
        omniExecutor.execute(
                () -> {
                    shutdownOmniConversationDetached(c);
                    try {
                        sendJson(
                                w,
                                Map.of(
                                        "type",
                                        "asr_realtime_closed",
                                        "payload",
                                        Map.of("turnId", tid, "code", 0, "reason", "client_requested")));
                    } catch (IOException ignored) {
                        /* ignore */
                    }
                });
    }

    private void dispatchOmniDownstreamEvent(
            String sid, String boundTurnId, OmniRealtimeConversation conv, JsonObject message) {
        if (conv == null || message == null) {
            return;
        }
        String type = message.has("type") && !message.get("type").isJsonNull() ? message.get("type").getAsString() : "";
        Optional<String> tr = VideoInterviewOmniAsrSupport.extractTranscriptText(message);
        if (tr.isEmpty()) {
            log.trace("Omni event without transcript type={} session={}", type, sid);
            return;
        }
        String segment = tr.get();
        boolean completed = VideoInterviewOmniAsrSupport.isTranscriptionCompletedEvent(type);
        boolean partial =
                !completed
                        && (VideoInterviewOmniAsrSupport.isTranscriptionPartialEvent(type)
                                || type.contains("input_audio_transcription"));
        WebSocketSession wsRef = null;
        String outTurnId = null;
        boolean sendPartial = false;
        boolean sendCommit = false;
        synchronized (lock(sid)) {
            MutableSession m = live.get(sid);
            if (m == null || m.omniAsr != conv || !boundTurnId.equals(m.currentTurnId)) {
                return;
            }
            wsRef = m.ws;
            outTurnId = m.currentTurnId;
            if (completed) {
                appendCommittedSegment(m.realtimeAsrCommitted, segment);
                sendCommit = true;
            } else if (partial) {
                sendPartial = true;
            }
        }
        if (wsRef == null || !wsRef.isOpen()) {
            return;
        }
        try {
            if (sendPartial) {
                sendJson(wsRef, Map.of("type", "asr_partial", "payload", Map.of("turnId", outTurnId, "text", segment)));
            }
            if (sendCommit) {
                sendJson(wsRef, Map.of("type", "asr_commit", "payload", Map.of("turnId", outTurnId, "text", segment)));
            }
        } catch (IOException e) {
            log.debug("Omni forward to client failed session={}", sid, e);
        }
    }

    private void dispatchOmniTransportClosed(
            String sid, String boundTurnId, OmniRealtimeConversation conv, int code, String reason) {
        WebSocketSession wsRef = null;
        synchronized (lock(sid)) {
            MutableSession m = live.get(sid);
            if (m == null) {
                return;
            }
            if (conv != null && m.omniAsr == conv) {
                m.omniAsr = null;
                m.omniReady = false;
                m.omniPendingB64.clear();
            }
            wsRef = m.ws;
        }
        if (wsRef == null || !wsRef.isOpen()) {
            return;
        }
        try {
            Map<String, Object> p = new HashMap<>();
            p.put("turnId", boundTurnId);
            p.put("code", code);
            p.put("reason", reason == null ? "" : reason);
            sendJson(wsRef, Map.of("type", "asr_realtime_closed", "payload", p));
        } catch (IOException ignored) {
            /* ignore */
        }
    }

    private void shutdownOmniForAnswer(MutableSession ms) {
        ms.omniPendingB64.clear();
        OmniRealtimeConversation c = ms.omniAsr;
        ms.omniAsr = null;
        ms.omniReady = false;
        shutdownOmniConversationDetached(c);
    }

    private void shutdownOmniConversationDetached(OmniRealtimeConversation c) {
        if (c == null) {
            return;
        }
        try {
            c.endSession();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.debug("OmniRealtimeConversation.endSession", e);
        }
        try {
            c.close();
        } catch (Exception e) {
            log.debug("OmniRealtimeConversation.close", e);
        }
    }

    private static void flushOmniPending(MutableSession ms) {
        if (ms.omniAsr == null) {
            ms.omniPendingB64.clear();
            return;
        }
        String chunk;
        while ((chunk = ms.omniPendingB64.pollFirst()) != null) {
            try {
                ms.omniAsr.appendAudio(chunk);
            } catch (Exception e) {
                log.debug("flushOmniPending appendAudio failed", e);
                break;
            }
        }
    }

    private static void appendCommittedSegment(StringBuilder sb, String segment) {
        if (segment == null) {
            return;
        }
        String rest = stripLeadingOverlapAfterTail(sb, segment, 2, 96);
        if (rest.isEmpty()) {
            return;
        }
        sb.append(rest);
    }

    /**
     * 若 segment 开头与 sb 尾部存在重复（切片 / 多次定稿边界），只追加去重后的剩余部分。
     */
    private static String stripLeadingOverlapAfterTail(StringBuilder sb, String segment, int minOverlap, int maxScan) {
        String s = segment.trim();
        if (s.isEmpty()) {
            return "";
        }
        if (sb.isEmpty()) {
            return s;
        }
        int end = sb.length();
        while (end > 0 && Character.isWhitespace(sb.charAt(end - 1))) {
            end--;
        }
        if (end == 0) {
            return s;
        }
        int start = Math.max(0, end - maxScan);
        String tail = sb.substring(start, end);
        int maxK = Math.min(tail.length(), s.length());
        for (int k = maxK; k >= minOverlap; k--) {
            if (tail.regionMatches(tail.length() - k, s, 0, k)) {
                return s.substring(k);
            }
        }
        return s;
    }

    public void connectionClosed(String sessionId, WebSocketSession closed) {
        synchronized (lock(sessionId)) {
            MutableSession ms = live.get(sessionId);
            if (ms != null && closed.equals(ms.ws)) {
                if (ms.fsm == InterviewFsm.SESSION_CLOSING) {
                    return;
                }
                shutdownOmniForAnswer(ms);
                live.remove(sessionId);
            }
        }
    }

    private enum InterviewFsm {
        QUESTION_STREAMING,
        AWAITING_ANSWER,
        RECORDING,
        AGENT_PROCESSING,
        POST_TURN_REVIEW,
        SESSION_CLOSING,
        ENDED
    }

    private static final class MutableSession {
        private WebSocketSession ws;
        private InterviewFsm fsm = InterviewFsm.QUESTION_STREAMING;
        private String currentTurnId = "";
        private boolean lastAgentShouldEnd;
        private String lastNextQuestionHint = "";
        /** 百炼 Omni 实时转写（单轮作答内单实例）。 */
        private OmniRealtimeConversation omniAsr;
        /** connect/updateSession 完成前为 false，音频帧先入队 {@link #omniPendingB64}。 */
        private volatile boolean omniReady;
        private final ArrayDeque<String> omniPendingB64 = new ArrayDeque<>();
        private final StringBuilder realtimeAsrCommitted = new StringBuilder();

        private MutableSession() {
        }
    }
}
