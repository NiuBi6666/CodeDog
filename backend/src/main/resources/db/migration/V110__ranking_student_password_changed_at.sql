ALTER TABLE ranking_student_accounts
  ADD COLUMN password_changed_at TIMESTAMP(6) NULL AFTER updated_at;
