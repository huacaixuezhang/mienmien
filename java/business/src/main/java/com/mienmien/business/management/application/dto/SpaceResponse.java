package com.mienmien.business.management.application.dto;

import com.mienmien.business.management.domain.model.Space;

public record SpaceResponse(
        String spaceId,
        String ownerUserId,
        String name,
        String status,
        String createdAt,
        String updatedAt,
        String deletedAt
) {
    public static SpaceResponse from(Space s) {
        return new SpaceResponse(
                s.getSpaceId(),
                s.getOwnerUserId(),
                s.getName(),
                s.getStatus(),
                s.getCreatedAt().toString(),
                s.getUpdatedAt().toString(),
                s.getDeletedAt() == null ? null : s.getDeletedAt().toString()
        );
    }
}
