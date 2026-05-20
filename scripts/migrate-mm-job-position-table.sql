USE MienMieApp;

CREATE TABLE IF NOT EXISTS mm_job_position (
  position_id VARCHAR(64) NOT NULL PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL DEFAULT '',
  space_id VARCHAR(64) NULL DEFAULT NULL,
  title VARCHAR(256) NOT NULL,
  company VARCHAR(256) NOT NULL DEFAULT '',
  location VARCHAR(256) NOT NULL DEFAULT '',
  base_range LONGTEXT NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mm_job_position_space (
  position_id VARCHAR(64) NOT NULL,
  space_id VARCHAR(64) NOT NULL,
  PRIMARY KEY (position_id, space_id),
  KEY idx_jps_space (space_id),
  CONSTRAINT fk_jps_position FOREIGN KEY (position_id) REFERENCES mm_job_position (position_id) ON DELETE CASCADE,
  CONSTRAINT fk_jps_space FOREIGN KEY (space_id) REFERENCES mm_space (space_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
