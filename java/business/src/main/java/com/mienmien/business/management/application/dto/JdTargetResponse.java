package com.mienmien.business.management.application.dto;

import com.mienmien.business.management.domain.model.JdTarget;

public record JdTargetResponse(
        String jdId,
        String spaceId,
        String sourceType,
        String rawText,
        String focusPoints
) {
    public static JdTargetResponse from(JdTarget j) {
        return new JdTargetResponse(
                j.getJdId(),
                j.getSpaceId(),
                j.getSourceType(),
                j.getRawText(),
                j.getFocusPoints()
        );
    }
}
