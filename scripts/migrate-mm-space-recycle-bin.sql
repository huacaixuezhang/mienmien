USE MienMieApp;

ALTER TABLE mm_space
  ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL DEFAULT NULL;

SET @idx_exists = (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'mm_space'
    AND index_name = 'idx_space_deleted_at'
);
SET @idx_sql = IF(@idx_exists = 0, 'CREATE INDEX idx_space_deleted_at ON mm_space (deleted_at)', 'SELECT 1');
PREPARE stmt FROM @idx_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
