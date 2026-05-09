USE MienMieApp;

ALTER TABLE mm_resume
  ADD UNIQUE KEY uk_resume_space_version (space_id, version);
