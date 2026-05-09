USE MienMieApp;

ALTER TABLE mm_standard_answer_bank
  ADD COLUMN IF NOT EXISTS cards_json LONGTEXT NOT NULL DEFAULT '';
