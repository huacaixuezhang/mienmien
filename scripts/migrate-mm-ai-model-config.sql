USE MienMieApp;

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
