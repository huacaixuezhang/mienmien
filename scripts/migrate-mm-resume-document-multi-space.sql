-- 简历多空间关联：将 mm_resume_document.space_id 拆到 mm_resume_document_space。
-- 在已有旧表结构的数据库上执行一次；全新安装请直接使用 scripts/seed-mienmien.sql，无需执行本文件。
-- 若 INSERT/ALTER 因环境差异不便执行，可改用 scripts/migrate-mm-resume-document-relax-legacy-space-id.sql，
-- 或依赖 business 启动时 JdbcResumeDocumentRepository 内的幂等对齐逻辑。
-- 执行前请备份。

USE MienMieApp;

CREATE TABLE IF NOT EXISTS mm_resume_document_space (
  resume_id VARCHAR(64) NOT NULL,
  space_id VARCHAR(64) NOT NULL,
  PRIMARY KEY (resume_id, space_id),
  KEY idx_rds_space (space_id),
  CONSTRAINT fk_rds_resume FOREIGN KEY (resume_id) REFERENCES mm_resume_document (resume_id) ON DELETE CASCADE,
  CONSTRAINT fk_rds_space FOREIGN KEY (space_id) REFERENCES mm_space (space_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 若 mm_resume_document 仍含 space_id 列，取消下行注释并执行；已迁移过的库请勿重复执行
-- INSERT IGNORE INTO mm_resume_document_space (resume_id, space_id)
--   SELECT resume_id, space_id FROM mm_resume_document WHERE space_id IS NOT NULL AND space_id <> '';
-- ALTER TABLE mm_resume_document DROP FOREIGN KEY fk_resume_document_space;
-- ALTER TABLE mm_resume_document DROP INDEX idx_resume_document_space;
-- ALTER TABLE mm_resume_document DROP COLUMN space_id;
