-- 面试记录与岗位的绑定（一条面试记录最多绑定一个岗位；删除面试或岗位时级联清理关联行）
USE MienMieApp;

CREATE TABLE IF NOT EXISTS mm_interview_record_job (
  record_id VARCHAR(64) NOT NULL PRIMARY KEY,
  position_id VARCHAR(64) NOT NULL,
  CONSTRAINT fk_irj_record FOREIGN KEY (record_id) REFERENCES mm_interview_record (record_id) ON DELETE CASCADE,
  CONSTRAINT fk_irj_position FOREIGN KEY (position_id) REFERENCES mm_job_position (position_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
