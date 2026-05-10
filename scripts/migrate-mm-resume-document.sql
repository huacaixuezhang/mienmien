-- 独立简历表：一行 = 一份简历（名称 + 模块 JSON），归属用户与空间。
-- 执行前请确认库名（默认与 seed 一致 MienMieApp）。

USE MienMieApp;

-- 与 scripts/seed-mienmien.sql 一致：简历主体不存 space_id，多空间见 mm_resume_document_space。
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
