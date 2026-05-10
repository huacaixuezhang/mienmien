CREATE DATABASE IF NOT EXISTS MienMieApp
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE MienMieApp;

CREATE TABLE IF NOT EXISTS mm_space (
  space_id VARCHAR(64) NOT NULL PRIMARY KEY,
  owner_user_id VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP NULL DEFAULT NULL,
  KEY idx_space_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mm_user_account (
  user_id VARCHAR(64) NOT NULL PRIMARY KEY,
  phone VARCHAR(32) NOT NULL,
  password VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mm_business_session (
  session_token VARCHAR(64) NOT NULL PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMP NOT NULL,
  KEY idx_business_session_user (user_id),
  KEY idx_business_session_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mm_resume (
  resume_id VARCHAR(64) NOT NULL PRIMARY KEY,
  space_id VARCHAR(64) NOT NULL,
  version INT NOT NULL,
  content TEXT NOT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_resume_space_version (space_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mm_resume_document (
  resume_id VARCHAR(64) NOT NULL PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  name VARCHAR(256) NOT NULL,
  modules_json LONGTEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_resume_document_user (user_id),
  CONSTRAINT fk_resume_document_user FOREIGN KEY (user_id) REFERENCES mm_user_account (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mm_resume_document_space (
  resume_id VARCHAR(64) NOT NULL,
  space_id VARCHAR(64) NOT NULL,
  PRIMARY KEY (resume_id, space_id),
  KEY idx_rds_space (space_id),
  CONSTRAINT fk_rds_resume FOREIGN KEY (resume_id) REFERENCES mm_resume_document (resume_id) ON DELETE CASCADE,
  CONSTRAINT fk_rds_space FOREIGN KEY (space_id) REFERENCES mm_space (space_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mm_job_position (
  position_id VARCHAR(64) NOT NULL PRIMARY KEY,
  space_id VARCHAR(64) NOT NULL,
  title VARCHAR(256) NOT NULL,
  company VARCHAR(256) NOT NULL DEFAULT '',
  location VARCHAR(256) NOT NULL DEFAULT '',
  base_range VARCHAR(8000) NOT NULL DEFAULT '',
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mm_jd_target (
  jd_id VARCHAR(64) NOT NULL PRIMARY KEY,
  space_id VARCHAR(64) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  raw_text TEXT NOT NULL,
  focus_points TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mm_standard_answer_bank (
  answer_id VARCHAR(64) NOT NULL PRIMARY KEY,
  space_id VARCHAR(64) NOT NULL,
  intro TEXT NOT NULL,
  reason TEXT NOT NULL,
  strengths TEXT NOT NULL,
  project TEXT NOT NULL,
  hr TEXT NOT NULL,
  cards_json LONGTEXT NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_answer_bank_space (space_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mm_interview_record (
  record_id VARCHAR(64) NOT NULL PRIMARY KEY,
  space_id VARCHAR(64) NOT NULL,
  type VARCHAR(16) NOT NULL,
  round INT NOT NULL,
  interview_type VARCHAR(32) NOT NULL,
  score INT NOT NULL,
  result VARCHAR(32) NOT NULL,
  summary TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mm_ai_model_config (
  config_id VARCHAR(64) NOT NULL PRIMARY KEY,
  owner_user_id VARCHAR(64) NOT NULL,
  provider VARCHAR(64) NOT NULL DEFAULT 'aliyun-bailian',
  base_url VARCHAR(512) NOT NULL DEFAULT '',
  api_key VARCHAR(1024) NOT NULL DEFAULT '',
  model_name VARCHAR(128) NOT NULL DEFAULT '',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ai_model_config_user (owner_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mm_guidance_session (
  session_id VARCHAR(64) NOT NULL PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  mode VARCHAR(16) NOT NULL,
  status VARCHAR(32) NOT NULL,
  started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ended_at TIMESTAMP NULL DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mm_question_event (
  event_id VARCHAR(64) NOT NULL PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL,
  source VARCHAR(16) NOT NULL,
  question_text TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mm_answer_stream (
  stream_id VARCHAR(64) NOT NULL PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL,
  question_event_id VARCHAR(64) NULL,
  final_answer TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @col_space_deleted_at_exists = (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'mm_space'
    AND column_name = 'deleted_at'
);
SET @col_space_deleted_at_sql = IF(
  @col_space_deleted_at_exists = 0,
  'ALTER TABLE mm_space ADD COLUMN deleted_at TIMESTAMP NULL DEFAULT NULL',
  'SELECT 1'
);
PREPARE stmt_col_space_deleted_at FROM @col_space_deleted_at_sql;
EXECUTE stmt_col_space_deleted_at;
DEALLOCATE PREPARE stmt_col_space_deleted_at;

SET @idx_space_deleted_at_exists = (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'mm_space'
    AND index_name = 'idx_space_deleted_at'
);
SET @idx_space_deleted_at_sql = IF(
  @idx_space_deleted_at_exists = 0,
  'CREATE INDEX idx_space_deleted_at ON mm_space (deleted_at)',
  'SELECT 1'
);
PREPARE stmt_idx_space_deleted_at FROM @idx_space_deleted_at_sql;
EXECUTE stmt_idx_space_deleted_at;
DEALLOCATE PREPARE stmt_idx_space_deleted_at;

SET @col_answer_cards_json_exists = (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'mm_standard_answer_bank'
    AND column_name = 'cards_json'
);
SET @col_answer_cards_json_sql = IF(
  @col_answer_cards_json_exists = 0,
  'ALTER TABLE mm_standard_answer_bank ADD COLUMN cards_json LONGTEXT NOT NULL DEFAULT ''''''',
  'SELECT 1'
);
PREPARE stmt_col_answer_cards_json FROM @col_answer_cards_json_sql;
EXECUTE stmt_col_answer_cards_json;
DEALLOCATE PREPARE stmt_col_answer_cards_json;

INSERT IGNORE INTO mm_user_account (user_id, phone, password, created_at, updated_at)
VALUES ('user_001', '13800138000', 'dev123456', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO mm_space (space_id, owner_user_id, name, status)
VALUES ('sp_1001', 'user_001', '求职空间A', 'ACTIVE');
