package com.mienmien.business.management.application.dto;

import java.time.Instant;
import java.util.List;

public record JobPositionResponse(
        String positionId,
        /** 兼容旧客户端：取关联空间的第一个 */
        String spaceId,
        List<String> spaceIds,
        String title,
        String company,
        String location,
        String baseRange,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
