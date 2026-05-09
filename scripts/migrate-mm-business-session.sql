USE MienMieApp;

ALTER TABLE mm_user_account MODIFY COLUMN password VARCHAR(255) NOT NULL;

CREATE TABLE IF NOT EXISTS mm_business_session (
  session_token VARCHAR(64) NOT NULL PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMP NOT NULL,
  KEY idx_business_session_user (user_id),
  KEY idx_business_session_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
