package com.mienmien.business.management.application.dto;

import java.time.Instant;

public record UserInterviewerRoleResponse(
        String roleId,
        String roleCode,
        String roleName,
        String interviewContent,
        String focusPoints,
        String evaluationHint,
        Instant createdAt,
        Instant updatedAt) {}
