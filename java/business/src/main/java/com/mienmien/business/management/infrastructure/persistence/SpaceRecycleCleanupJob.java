package com.mienmien.business.management.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Component
public class SpaceRecycleCleanupJob {
    private final JdbcTemplate jdbcTemplate;

    public SpaceRecycleCleanupJob(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "${mienmien.business.recycle.cleanup-cron:0 30 3 * * *}")
    @Transactional
    public void cleanupRecycledSpaces() {
        jdbcTemplate.update("DELETE FROM mm_business_session WHERE expires_at <= ?", Timestamp.from(Instant.now()));
        List<String> expiredIds = jdbcTemplate.query(
                "SELECT space_id FROM mm_space WHERE status = 'RECYCLED' AND deleted_at IS NOT NULL "
                        + "AND deleted_at < DATE_SUB(NOW(), INTERVAL 30 DAY)",
                (rs, rowNum) -> rs.getString("space_id")
        );
        for (String spaceId : expiredIds) {
            jdbcTemplate.update("DELETE FROM mm_ai_model_config WHERE space_id = ?", spaceId);
            jdbcTemplate.update("DELETE FROM mm_standard_answer_bank WHERE space_id = ?", spaceId);
            jdbcTemplate.update("DELETE FROM mm_resume WHERE space_id = ?", spaceId);
            jdbcTemplate.update("DELETE FROM mm_jd_target WHERE space_id = ?", spaceId);
            jdbcTemplate.update("DELETE FROM mm_job_position WHERE space_id = ?", spaceId);
            jdbcTemplate.update("DELETE FROM mm_interview_record WHERE space_id = ?", spaceId);
            jdbcTemplate.update("DELETE FROM mm_space WHERE space_id = ?", spaceId);
        }
    }
}
