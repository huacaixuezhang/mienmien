package com.mienmien.consumer.guidance.interfaces.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mienmien.consumer.guidance.application.diarization.engine.EngineResolver;
import com.mienmien.consumer.guidance.application.diarization.engine.ProcessingResult;
import com.mienmien.consumer.guidance.application.diarization.engine.SpeakerDiarizationEngine;
import com.mienmien.consumer.guidance.application.diarization.turn.TurnDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SpeakerDiarizationWebSocketHandler extends AbstractWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(SpeakerDiarizationWebSocketHandler.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, SessionContext> contexts = new ConcurrentHashMap<>();
    private final EngineResolver engineResolver;
    private final TurnDetectionService turnDetectionService;

    public SpeakerDiarizationWebSocketHandler(
            EngineResolver engineResolver,
            TurnDetectionService turnDetectionService) {
        this.engineResolver = engineResolver;
        this.turnDetectionService = turnDetectionService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        SessionContext ctx = new SessionContext();
        ctx.engine = engineResolver.resolve("unsupervised");
        ctx.engine.init(Map.of());
        ctx.sessionId = "ws_" + session.getId();
        contexts.put(session.getId(), ctx);
        log.info("ws connected {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = objectMapper.readTree(message.getPayload());
        SessionContext ctx = contexts.get(session.getId());
        if (ctx == null) {
            return;
        }
        String type = node.path("type").asText("");
        if ("config".equals(type)) {
            String mode = node.path("mode").asText("unsupervised");
            ctx.engine = engineResolver.resolve(mode);
            ctx.engine.init(Map.of());
            if (node.hasNonNull("sessionId")) {
                ctx.sessionId = node.get("sessionId").asText();
            }
            sendJson(session, Map.of("type", "config_ack", "mode", mode, "sessionId", ctx.sessionId));
            return;
        }
        if ("enrollment".equals(type) && node.hasNonNull("enrollmentId") && node.hasNonNull("audioBase64")) {
            byte[] data = java.util.Base64.getDecoder().decode(node.get("audioBase64").asText());
            ctx.engine.registerEnrollment(node.get("enrollmentId").asText(), data);
            sendJson(session, Map.of("type", "enrollment_ack"));
            return;
        }
        if ("ping".equals(type)) {
            sendJson(session, Map.of("type", "pong"));
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        SessionContext ctx = contexts.get(session.getId());
        if (ctx == null || ctx.engine == null) {
            return;
        }
        byte[] frame = new byte[message.getPayloadLength()];
        message.getPayload().get(frame);
        ctx.engine.process(frame).thenAccept(result -> {
            if (result != null) {
                onResult(session, ctx, result);
            }
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SessionContext ctx = contexts.remove(session.getId());
        if (ctx != null && ctx.engine != null) {
            ctx.engine.destroy();
        }
        log.info("ws closed {}", session.getId());
    }

    private void onResult(WebSocketSession session, SessionContext ctx, ProcessingResult result) {
        try {
            sendJson(session, Map.of(
                    "type", "transcription",
                    "speaker", result.speakerId(),
                    "text", result.text(),
                    "confidence", result.confidence(),
                    "partial", result.partial(),
                    "timestamp", result.timestamp()
            ));
            turnDetectionService.onTranscriptionFrame(
                    ctx.sessionId,
                    result.speakerId(),
                    result.text(),
                    result.partial(),
                    result.confidence()
            );
        } catch (Exception e) {
            try {
                sendJson(session, Map.of("type", "error", "message", e.getMessage()));
            } catch (IOException ignored) {
            }
        }
    }

    private void sendJson(WebSocketSession session, Map<String, Object> payload) throws IOException {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
    }

    private static class SessionContext {
        private SpeakerDiarizationEngine engine;
        private String sessionId;
    }
}
