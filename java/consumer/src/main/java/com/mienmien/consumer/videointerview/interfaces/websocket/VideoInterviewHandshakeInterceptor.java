package com.mienmien.consumer.videointerview.interfaces.websocket;

import com.mienmien.consumer.videointerview.infrastructure.persistence.VideoInterviewJdbcStore;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.Optional;

@Component
public class VideoInterviewHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "videoInterview.userId";
    public static final String ATTR_VIDEO_SESSION_ID = "videoInterview.sessionId";

    private final VideoInterviewJdbcStore store;

    public VideoInterviewHandshakeInterceptor(VideoInterviewJdbcStore store) {
        this.store = store;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }
        var http = servletRequest.getServletRequest();
        String token = http.getParameter("sessionToken");
        Optional<String> uid = store.findUserIdByBusinessSessionToken(token);
        if (uid.isEmpty()) {
            return false;
        }
        String path = http.getRequestURI();
        int slash = path.lastIndexOf('/');
        if (slash < 0 || slash >= path.length() - 1) {
            return false;
        }
        String sessionId = path.substring(slash + 1);
        var row = store.loadSession(sessionId);
        if (row.isEmpty() || !uid.get().equals(row.get().userId())) {
            return false;
        }
        attributes.put(ATTR_USER_ID, uid.get());
        attributes.put(ATTR_VIDEO_SESSION_ID, sessionId);
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        /* no-op */
    }
}
