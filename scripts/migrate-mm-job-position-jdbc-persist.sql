-- 岗位持久化：增加 owner、可空主 space；多空间关联表（与 JobPositionApplicationService JDBC 实现一致）
USE MienMieApp;

SET @col_uid = (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'mm_job_position' AND column_name = 'user_id'
);
SET @sql_uid = IF(
  @col_uid = 0,
  'ALTER TABLE mm_job_position ADD COLUMN user_id VARCHAR(64) NOT NULL DEFAULT '''' AFTER position_id',
  'SELECT 1'
);
PREPARE stmt_uid FROM @sql_uid;
EXECUTE stmt_uid;
DEALLOCATE PREPARE stmt_uid;

ALTER TABLE mm_job_position MODIFY COLUMN space_id VARCHAR(64) NULL DEFAULT NULL;

CREATE TABLE IF NOT EXISTS mm_job_position_space (
  position_id VARCHAR(64) NOT NULL,
  space_id VARCHAR(64) NOT NULL,
  PRIMARY KEY (position_id, space_id),
  KEY idx_jps_space (space_id),
  CONSTRAINT fk_jps_position FOREIGN KEY (position_id) REFERENCES mm_job_position (position_id) ON DELETE CASCADE,
  CONSTRAINT fk_jps_space FOREIGN KEY (space_id) REFERENCES mm_space (space_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 将历史「单 space_id」迁入关联表（若尚未存在）
INSERT IGNORE INTO mm_job_position_space (position_id, space_id)
SELECT position_id, space_id FROM mm_job_position
WHERE space_id IS NOT NULL AND TRIM(space_id) <> '';
