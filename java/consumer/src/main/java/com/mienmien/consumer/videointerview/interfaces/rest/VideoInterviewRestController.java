package com.mienmien.consumer.videointerview.interfaces.rest;

import com.mienmien.consumer.videointerview.infrastructure.ai.DashscopeQwenAsrClient;
import com.mienmien.consumer.videointerview.infrastructure.persistence.VideoInterviewJdbcStore;
import com.mienmien.consumer.videointerview.infrastructure.persistence.VideoInterviewJdbcStore.VideoInterviewEventRow;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/v1/consumer/video-interview")
public class VideoInterviewRestController {

    private static final ExecutorService EXEC = Executors.newVirtualThreadPerTaskExecutor();

    private final VideoInterviewJdbcStore store;
    private final DashscopeQwenAsrClient dashscopeQwenAsrClient;

    public VideoInterviewRestController(VideoInterviewJdbcStore store, DashscopeQwenAsrClient dashscopeQwenAsrClient) {
        this.store = store;
        this.dashscopeQwenAsrClient = dashscopeQwenAsrClient;
    }

    @GetMapping("/sessions/{sessionId}/turns")
    public List<Map<String, Object>> listTurns(
            @PathVariable String sessionId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String uid = requireUserId(authorization);
        var row = store.loadSession(sessionId).orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (!uid.equals(row.userId())) {
            throw new IllegalArgumentException("无权访问该会话");
        }
        return store.listTurns(sessionId).stream().map(VideoInterviewRestController::turnToMap).toList();
    }

    private static Map<String, Object> turnToMap(VideoInterviewJdbcStore.VideoInterviewTurnRow t) {
        Map<String, Object> m = new HashMap<>();
        m.put("turnId", t.turnId());
        m.put("turnIndex", t.turnIndex());
        m.put("questionText", t.questionText());
        m.put("answerText", t.answerText());
        m.put("standardAnswer", t.standardAnswer());
        m.put("evaluationJson", t.evaluationJson());
        m.put("bridgingUtterance", t.bridgingUtterance() == null ? "" : t.bridgingUtterance());
        m.put("answerAttempt", t.answerAttempt());
        m.put("createdAt", t.createdAt().toString());
        m.put("answeredAt", t.answeredAt() != null ? t.answeredAt().toString() : null);
        return m;
    }

    @GetMapping("/sessions/{sessionId}/events")
    public List<Map<String, Object>> listEvents(
            @PathVariable String sessionId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String uid = requireUserId(authorization);
        var row = store.loadSession(sessionId).orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (!uid.equals(row.userId())) {
            throw new IllegalArgumentException("无权访问该会话");
        }
        return store.listEventsAfter(sessionId, 0).stream()
                .map(VideoInterviewRestController::toMap)
                .toList();
    }

    @GetMapping(value = "/sessions/{sessionId}/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(
            @PathVariable String sessionId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String uid = requireUserId(authorization);
        var row = store.loadSession(sessionId).orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (!uid.equals(row.userId())) {
            throw new IllegalArgumentException("无权访问该会话");
        }
        SseEmitter emitter = new SseEmitter(300_000L);
        EXEC.execute(
                () -> {
                    try {
                        for (VideoInterviewEventRow e : store.listEventsAfter(sessionId, 0)) {
                            emitter.send(SseEmitter.event().data(toMap(e)));
                        }
                        emitter.complete();
                    } catch (IOException ex) {
                        emitter.completeWithError(ex);
                    }
                });
        return emitter;
    }

    /**
     * 句末整段音频转写：经 consumer 调用百炼 Qwen-ASR（OpenAI 兼容 chat/completions），避免浏览器直连模型仓库导致的 CORS 问题。
     */
    @PostMapping(
            value = "/sessions/{sessionId}/asr/transcribe",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> transcribeAsr(
            @PathVariable String sessionId,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String uid = requireUserId(authorization);
        var row = store.loadSession(sessionId).orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (!uid.equals(row.userId())) {
            throw new IllegalArgumentException("无权访问该会话");
        }
        if (!dashscopeQwenAsrClient.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "code",
                            "CON-5030",
                            "message",
                            "服务器未配置百炼 API Key（spring.ai.dashscope.api-key），无法进行语音识别。",
                            "text",
                            ""));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "CON-4002", "message", "未收到音频文件", "text", ""));
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "CON-4003", "message", "读取上传文件失败", "text", ""));
        }
        if (bytes.length > 10 * 1024 * 1024) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "CON-4004", "message", "音频文件过大（上限约 10MB）", "text", ""));
        }
        String mime = file.getContentType();
        if (mime == null || mime.isBlank()) {
            mime = "audio/wav";
        }
        return dashscopeQwenAsrClient
                .transcribe(bytes, mime)
                .map(t -> ResponseEntity.<Map<String, Object>>ok(Map.of("text", t)))
                .orElseGet(
                        () -> ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                                .body(Map.of(
                                        "code",
                                        "CON-5020",
                                        "message",
                                        "语音识别服务暂时不可用或未返回文本，请稍后重试。",
                                        "text",
                                        "")));
    }

    private String requireUserId(String authorization) {
        return store.findUserIdByBusinessSessionToken(extractBearer(authorization))
                .orElseThrow(() -> new IllegalArgumentException("无效或过期的会话令牌"));
    }

    private static Map<String, Object> toMap(VideoInterviewEventRow e) {
        return Map.of(
                "eventId", e.eventId(),
                "seq", e.seq(),
                "type", e.type(),
                "payloadJson", e.payloadJson(),
                "createdAt", e.createdAt().toString());
    }

    private static String extractBearer(String authorization) {
        if (authorization == null) {
            return null;
        }
        String t = authorization.trim();
        if (t.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return t.substring(7).trim();
        }
        return null;
    }
}
