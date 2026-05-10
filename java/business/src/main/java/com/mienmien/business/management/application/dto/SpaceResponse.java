package com.mienmien.business.management.application.dto;

import java.time.Instant;

public record SpaceResponse(
        String spaceId,
        String ownerUserId,
        String name,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
) {
}
