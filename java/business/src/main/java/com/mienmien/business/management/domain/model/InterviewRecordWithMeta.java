package com.mienmien.business.management.domain.model;

import java.time.Instant;

/**
 * 面试记录与持久化元数据（创建时间、可选的岗位绑定）。
 */
public record InterviewRecordWithMeta(InterviewRecord record, Instant createdAt, String positionId) {
}
