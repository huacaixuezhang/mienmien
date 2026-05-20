package com.mienmien.business.management.infrastructure.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(4)
public class MmVideoInterviewSessionSchemaBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MmVideoInterviewSessionSchemaBootstrap.class);

    private final JdbcTemplate jdbcTemplate;
    private final boolean enabled;

    public MmVideoInterviewSessionSchemaBootstrap(
            JdbcTemplate jdbcTemplate,
            @Value("${mienmien.business.schema.auto-patch-video-interview-session:true}") boolean enabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        try {
            jdbcTemplate.execute(
                    """
                            CREATE TABLE IF NOT EXISTS mm_video_interview_session (
                              session_id VARCHAR(64) NOT NULL PRIMARY KEY,
                              user_id VARCHAR(64) NOT NULL,
                              space_id VARCHAR(64) NOT NULL,
                              business_record_id VARCHAR(64) NOT NULL,
                              position_id VARCHAR(64) NULL,
                              round_index INT NOT NULL DEFAULT 0,
                              style_key VARCHAR(128) NOT NULL DEFAULT '',
                              status VARCHAR(32) NOT NULL,
                              epoch BIGINT NOT NULL DEFAULT 0,
                              last_event_seq BIGINT NOT NULL DEFAULT 0,
                              resume_snapshot_json LONGTEXT NOT NULL,
                              job_snapshot_json LONGTEXT NOT NULL,
                              style_prompt_snapshot LONGTEXT NOT NULL,
                              orchestrator_model VARCHAR(128) NOT NULL DEFAULT '',
                              asr_model VARCHAR(128) NOT NULL DEFAULT '',
                              started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              ended_at TIMESTAMP NULL DEFAULT NULL,
                              KEY idx_video_session_space (space_id),
                              KEY idx_video_session_user (user_id),
                              KEY idx_video_session_record (business_record_id),
                              CONSTRAINT fk_video_session_space FOREIGN KEY (space_id) REFERENCES mm_space (space_id),
                              CONSTRAINT fk_video_session_record FOREIGN KEY (business_record_id) REFERENCES mm_interview_record (record_id) ON DELETE CASCADE
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                            """);
            jdbcTemplate.execute(
                    """
                            CREATE TABLE IF NOT EXISTS mm_video_interview_event (
                              event_id VARCHAR(64) NOT NULL PRIMARY KEY,
                              session_id VARCHAR(64) NOT NULL,
                              seq BIGINT NOT NULL,
                              type VARCHAR(48) NOT NULL,
                              payload_json LONGTEXT NOT NULL,
                              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              UNIQUE KEY uk_video_event_session_seq (session_id, seq),
                              KEY idx_video_event_session (session_id),
                              CONSTRAINT fk_video_event_session FOREIGN KEY (session_id) REFERENCES mm_video_interview_session (session_id) ON DELETE CASCADE
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                            """);
            jdbcTemplate.execute(
                    """
                            CREATE TABLE IF NOT EXISTS mm_video_interview_turn (
                              turn_id VARCHAR(64) NOT NULL PRIMARY KEY,
                              session_id VARCHAR(64) NOT NULL,
                              turn_index INT NOT NULL,
                              question_text LONGTEXT NOT NULL,
                              answer_text LONGTEXT NULL,
                              standard_answer LONGTEXT NULL,
                              evaluation_json LONGTEXT NULL,
                              agent_raw_json LONGTEXT NULL,
                              answer_attempt INT NOT NULL DEFAULT 0,
                              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              answered_at TIMESTAMP NULL DEFAULT NULL,
                              UNIQUE KEY uk_video_turn_session_idx (session_id, turn_index),
                              KEY idx_video_turn_session (session_id),
                              CONSTRAINT fk_video_turn_session FOREIGN KEY (session_id) REFERENCES mm_video_interview_session (session_id) ON DELETE CASCADE
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                            """);
            log.debug("已确保 mm_video_interview_session / mm_video_interview_event / mm_video_interview_turn 存在");
        } catch (Exception e) {
            log.error("视频面试表自检失败，请执行 scripts/migrate-mm-video-interview-session.sql", e);
            throw new IllegalStateException("mm_video_interview_session 表创建失败: " + e.getMessage(), e);
        }
    }
}
