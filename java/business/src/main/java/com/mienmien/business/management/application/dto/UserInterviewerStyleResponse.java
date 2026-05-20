package com.mienmien.business.management.application.dto;

import java.time.Instant;

public record UserInterviewerStyleResponse(
        String styleId, String title, String promptBody, Instant createdAt, Instant updatedAt) {}
