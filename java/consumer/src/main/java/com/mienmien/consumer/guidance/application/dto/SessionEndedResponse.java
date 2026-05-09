package com.mienmien.consumer.guidance.application.dto;

public record SessionEndedResponse(String sessionId, String status, String endedAt) {
}
