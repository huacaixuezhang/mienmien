-- 旧库升级：mm_resume_document 上若仍存在 NOT NULL 的 space_id，会导致应用 INSERT 不含该列时报错 1364。
-- 与 JdbcResumeDocumentRepository 启动时幂等逻辑一致；也可在维护窗口手工执行。
-- 执行前请备份。默认库名 MienMieApp，可按需修改 USE。

USE MienMieApp;

CREATE TABLE IF NOT EXISTS mm_resume_document_space (
  resume_id VARCHAR(64) NOT NULL,
  space_id VARCHAR(64) NOT NULL,
  PRIMARY KEY (resume_id, space_id),
  KEY idx_rds_space (space_id),
  CONSTRAINT fk_rds_resume FOREIGN KEY (resume_id) REFERENCES mm_resume_document (resume_id) ON DELETE CASCADE,
  CONSTRAINT fk_rds_space FOREIGN KEY (space_id) REFERENCES mm_space (space_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO mm_resume_document_space (resume_id, space_id)
SELECT resume_id, space_id FROM mm_resume_document
WHERE space_id IS NOT NULL AND space_id <> '';

ALTER TABLE mm_resume_document MODIFY COLUMN space_id VARCHAR(64) NULL;

-- 若已无应用依赖该列，可再执行（按需取消注释）：
-- ALTER TABLE mm_resume_document DROP FOREIGN KEY fk_resume_document_space;
-- ALTER TABLE mm_resume_document DROP INDEX idx_resume_document_space;
-- ALTER TABLE mm_resume_document DROP COLUMN space_id;
