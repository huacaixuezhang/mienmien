-- 一次性迁移：为已存在的 mm_guidance_session 增加 ended_at（新环境可直接用 seed 中的建表语句）。
USE MienMieApp;

ALTER TABLE mm_guidance_session
  ADD COLUMN ended_at TIMESTAMP NULL DEFAULT NULL AFTER started_at;
