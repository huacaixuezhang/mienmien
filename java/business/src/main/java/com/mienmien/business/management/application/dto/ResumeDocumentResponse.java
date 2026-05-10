package com.mienmien.business.management.application.dto;

import java.time.Instant;
import java.util.List;

public record ResumeDocumentResponse(
        String resumeId,
        /** 兼容旧客户端：取关联空间的第一个 */
        String spaceId,
        List<String> spaceIds,
        String userId,
        String name,
        List<ResumeModuleDto> modules,
        Instant createdAt,
        Instant updatedAt
) {
}
