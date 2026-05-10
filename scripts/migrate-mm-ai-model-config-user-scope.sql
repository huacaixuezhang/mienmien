-- 一次性迁移：mm_ai_model_config 从 space_id 改为 owner_user_id（同一用户全空间共享配置）。
-- 执行前请备份。若库已由应用启动时的自动对齐逻辑迁移过，请勿重复执行（会报列/索引不存在等错误）。
-- 开发环境也可依赖 JdbcAiModelConfigRepository 启动时的 schema 对齐，无需手工跑本文件。

USE MienMieApp;

ALTER TABLE mm_ai_model_config ADD COLUMN owner_user_id VARCHAR(64) NULL AFTER config_id;

UPDATE mm_ai_model_config c
INNER JOIN mm_space s ON s.space_id = c.space_id
SET c.owner_user_id = s.owner_user_id
WHERE c.owner_user_id IS NULL;

DELETE FROM mm_ai_model_config WHERE owner_user_id IS NULL;

DELETE c FROM mm_ai_model_config c
JOIN (
  SELECT owner_user_id, MIN(config_id) AS keep_id
  FROM mm_ai_model_config
  GROUP BY owner_user_id
) k ON c.owner_user_id = k.owner_user_id AND c.config_id <> k.keep_id;

ALTER TABLE mm_ai_model_config DROP INDEX uk_ai_model_config_space;
ALTER TABLE mm_ai_model_config DROP COLUMN space_id;
ALTER TABLE mm_ai_model_config MODIFY owner_user_id VARCHAR(64) NOT NULL;
ALTER TABLE mm_ai_model_config ADD UNIQUE KEY uk_ai_model_config_user (owner_user_id);
