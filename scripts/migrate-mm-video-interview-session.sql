-- 视频模拟面试：会话头与事件流（与 java/business、java/consumer 共用库）
-- 执行：mysql ... < scripts/migrate-mm-video-interview-session.sql

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
