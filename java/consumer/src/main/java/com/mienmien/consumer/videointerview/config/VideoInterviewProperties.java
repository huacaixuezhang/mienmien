package com.mienmien.consumer.videointerview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mienmien.video-interview")
public record VideoInterviewProperties(
        String dashscopeBaseUrl,
        String orchestratorModel,
        String judgeModel,
        String qwenAsrModel,
        int maxQuestionsPerSession,
        int maxEventsPerSession,
        /** 每轮作答后是否调用导演生成「衔接口语」（与 TurnAgent 独立；判停仍以 TurnAgent 为准） */
        boolean bridgingEnabled,
        /** 衔接语落库/下发前最大字符数（近似「字」） */
        int bridgingUtteranceMaxChars,
        boolean realtimeAsrEnabled,
        String realtimeAsrModel,
        String realtimeAsrWsUrl) {
    public VideoInterviewProperties {
        if (dashscopeBaseUrl == null || dashscopeBaseUrl.isBlank()) {
            dashscopeBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        }
        if (orchestratorModel == null || orchestratorModel.isBlank()) {
            orchestratorModel = "qwen-turbo";
        }
        if (judgeModel == null || judgeModel.isBlank()) {
            judgeModel = "qwen-turbo";
        }
        if (qwenAsrModel == null || qwenAsrModel.isBlank()) {
            qwenAsrModel = "qwen3-asr-flash";
        }
        if (maxQuestionsPerSession <= 0) {
            maxQuestionsPerSession = 20;
        }
        if (maxEventsPerSession <= 0) {
            maxEventsPerSession = 2000;
        }
        if (bridgingUtteranceMaxChars <= 0) {
            bridgingUtteranceMaxChars = 800;
        }
        if (bridgingUtteranceMaxChars > 1900) {
            bridgingUtteranceMaxChars = 1900;
        }
        if (realtimeAsrModel == null || realtimeAsrModel.isBlank()) {
            realtimeAsrModel = "qwen3-asr-flash-realtime";
        }
        if (realtimeAsrWsUrl == null || realtimeAsrWsUrl.isBlank()) {
            realtimeAsrWsUrl = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime";
        }
    }
}
