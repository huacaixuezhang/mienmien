USE MienMieApp;

CREATE TABLE IF NOT EXISTS mm_standard_answer_bank (
  answer_id VARCHAR(64) NOT NULL PRIMARY KEY,
  space_id VARCHAR(64) NOT NULL,
  intro TEXT NOT NULL,
  reason TEXT NOT NULL,
  strengths TEXT NOT NULL,
  project TEXT NOT NULL,
  hr TEXT NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_answer_bank_space (space_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
