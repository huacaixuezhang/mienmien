-- 扩大岗位 base_range，以承载 JSON 扩展（岗位类型、描述、JD 富文本等）
USE MienMieApp;

ALTER TABLE mm_job_position
  MODIFY COLUMN base_range TEXT NOT NULL;
