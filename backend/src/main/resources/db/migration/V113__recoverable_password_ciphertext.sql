ALTER TABLE users
  ADD COLUMN password_ciphertext TEXT NULL AFTER password_hash,
  ADD COLUMN display_name VARCHAR(100) NULL AFTER username;

UPDATE users SET display_name='Liam' WHERE is_admin=TRUE AND username='admin';

ALTER TABLE ranking_student_accounts
  ADD COLUMN password_ciphertext TEXT NULL AFTER password_hash;
