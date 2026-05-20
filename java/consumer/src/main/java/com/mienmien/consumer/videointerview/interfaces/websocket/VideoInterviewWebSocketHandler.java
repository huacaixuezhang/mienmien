package com.mienmien.consumer.videointerview.interfaces.websocket;

import com.mienmien.consumer.videointerview.application.VideoInterviewRuntimeService;
import com.mienmien.consumer.videointerview.infrastructure.persistence.VideoInterviewJdbcStore;
import com.mienmien.consumer.videointerview.infrastructure.persistence.VideoInterviewJdbcStore.VideoInterviewSessionRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Optional;

@Component
public class VideoInterviewWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(VideoInterviewWebSocketHandler.class);

    private final VideoInterviewJdbcStore store;
    private final VideoInterviewRuntimeService runtimeService;

    public VideoInterviewWebSocketHandler(VideoInterviewJdbcStore store, VideoInterviewRuntimeService runtimeService) {
        this.store = store;
        this.runtimeService = runtimeService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = attr(session, VideoInterviewHandshakeInterceptor.ATTR_VIDEO_SESSION_ID);
        String userId = attr(session, VideoInterviewHandshakeInterceptor.ATTR_USER_ID);
        long fromSeq = parseFromSeq(session.getUri());
        Optional<VideoInterviewSessionRow> row = store.loadSession(sessionId);
        if (row.isEmpty()) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        runtimeService.handleConnectionOpen(session, row.get(), userId, fromSeq);
    }

    private static String attr(WebSocketSession session, String key) {
        Object v = session.getAttributes().get(key);
        return v == null ? "" : v.toString();
    }

    private static long parseFromSeq(URI uri) {
        if (uri == null || uri.getQuery() == null) {
            return 0L;
        }
        for (String pair : uri.getQuery().split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && "fromSeq".equals(pair.substring(0, eq))) {
                try {
                    return Long.parseLong(pair.substring(eq + 1));
                } catch (NumberFormatException ignored) {
                    return 0L;
                }
            }
        }
        return 0L;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = attr(session, VideoInterviewHandshakeInterceptor.ATTR_VIDEO_SESSION_ID);
        String userId = attr(session, VideoInterviewHandshakeInterceptor.ATTR_USER_ID);
        Optional<VideoInterviewSessionRow> row = store.loadSession(sessionId);
        if (row.isEmpty()) {
            return;
        }
        runtimeService.handleClientJson(session, row.get(), userId, message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = attr(session, VideoInterviewHandshakeInterceptor.ATTR_VIDEO_SESSION_ID);
        runtimeService.connectionClosed(sessionId, session);
        log.debug("videoInterview.ws.closed session={} status={}", sessionId, status);
    }
}
