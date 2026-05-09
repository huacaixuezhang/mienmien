package com.mienmien.business.management.application.dto;

import com.mienmien.business.management.domain.model.Resume;

public record ResumeResponse(
        String resumeId,
        String spaceId,
        int version,
        String content,
        boolean isActive,
        String updatedAt
) {
    public static ResumeResponse from(Resume r) {
        return new ResumeResponse(
                r.getResumeId(),
                r.getSpaceId(),
                r.getVersion(),
                r.getContent(),
                r.isActive(),
                r.getUpdatedAt().toString()
        );
    }
}
