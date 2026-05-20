package com.mienmien.consumer.videointerview.interfaces.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
public class VideoInterviewWebSocketConfigurer implements WebSocketConfigurer {
    private final VideoInterviewWebSocketHandler videoInterviewWebSocketHandler;
    private final VideoInterviewHandshakeInterceptor videoInterviewHandshakeInterceptor;

    public VideoInterviewWebSocketConfigurer(
            VideoInterviewWebSocketHandler videoInterviewWebSocketHandler,
            VideoInterviewHandshakeInterceptor videoInterviewHandshakeInterceptor) {
        this.videoInterviewWebSocketHandler = videoInterviewWebSocketHandler;
        this.videoInterviewHandshakeInterceptor = videoInterviewHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(videoInterviewWebSocketHandler, "/ws/consumer/video-interview/**")
                .addInterceptors(videoInterviewHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
