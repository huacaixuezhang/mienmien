-- 用户自定义「面试官角色」定义（与 mm_user_interviewer_style 并列，按账号存储）
CREATE TABLE IF NOT EXISTS mm_user_interviewer_role (
  role_id VARCHAR(64) NOT NULL PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  role_code VARCHAR(64) NOT NULL,
  role_name VARCHAR(256) NOT NULL,
  interview_content LONGTEXT NOT NULL,
  focus_points LONGTEXT NOT NULL,
  evaluation_hint LONGTEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_uir_user_code (user_id, role_code),
  KEY idx_uir_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
