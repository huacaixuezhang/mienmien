-- 视频模拟面试：按轮次结构化存储（与 consumer 运行时一致）
-- mysql ... < scripts/migrate-mm-video-interview-turn.sql

CREATE TABLE IF NOT EXISTS mm_video_interview_turn (
  turn_id VARCHAR(64) NOT NULL PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL,
  turn_index INT NOT NULL,
  question_text LONGTEXT NOT NULL,
  answer_text LONGTEXT NULL,
  standard_answer LONGTEXT NULL,
  evaluation_json LONGTEXT NULL,
  agent_raw_json LONGTEXT NULL,
  bridging_utterance VARCHAR(2000) NULL,
  answer_attempt INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  answered_at TIMESTAMP NULL DEFAULT NULL,
  UNIQUE KEY uk_video_turn_session_idx (session_id, turn_index),
  KEY idx_video_turn_session (session_id),
  CONSTRAINT fk_video_turn_session FOREIGN KEY (session_id) REFERENCES mm_video_interview_session (session_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
