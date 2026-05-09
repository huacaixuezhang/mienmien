package com.mienmien.business.management.application.dto;

import com.mienmien.business.management.domain.model.JobPosition;

public record JobPositionResponse(
        String positionId,
        String spaceId,
        String title,
        String company,
        String location,
        String baseRange,
        String status,
        String createdAt,
        String updatedAt
) {
    public static JobPositionResponse from(JobPosition p) {
        return new JobPositionResponse(
                p.getPositionId(),
                p.getSpaceId(),
                p.getTitle(),
                p.getCompany(),
                p.getLocation(),
                p.getBaseRange(),
                p.getStatus(),
                p.getCreatedAt().toString(),
                p.getUpdatedAt().toString()
        );
    }
}
