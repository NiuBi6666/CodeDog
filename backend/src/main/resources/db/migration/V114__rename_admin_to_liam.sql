-- Preserve all tenant data while changing the administrator's actual username.
-- Existing recoverable ciphertext is cleared because its authenticated context contains the old username.
SET @previous_foreign_key_checks = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;

UPDATE ranking_student_accounts
SET password_ciphertext = NULL
WHERE owner_username = 'admin' AND password_ciphertext IS NOT NULL;

UPDATE users
SET password_ciphertext = NULL
WHERE username = 'admin' AND password_ciphertext IS NOT NULL;

UPDATE crm_external_contact_observations SET owner_username = 'Liam' WHERE owner_username = 'admin';
UPDATE crm_external_contacts SET owner_username = 'Liam' WHERE owner_username = 'admin';
UPDATE ranking_announcements SET owner_username = 'Liam' WHERE owner_username = 'admin';
UPDATE ranking_board_settings SET owner_username = 'Liam' WHERE owner_username = 'admin';
UPDATE ranking_camps SET owner_username = 'Liam' WHERE owner_username = 'admin';
UPDATE ranking_classes SET owner_username = 'Liam' WHERE owner_username = 'admin';
UPDATE ranking_daily_snapshots SET owner_username = 'Liam' WHERE owner_username = 'admin';
UPDATE ranking_extension_devices SET owner_username = 'Liam' WHERE owner_username = 'admin';
UPDATE ranking_import_batches SET owner_username = 'Liam' WHERE owner_username = 'admin';
UPDATE ranking_import_batches SET actor = 'Liam' WHERE actor = 'admin';
UPDATE ranking_lesson_results SET owner_username = 'Liam' WHERE owner_username = 'admin';
UPDATE ranking_lessons SET owner_username = 'Liam' WHERE owner_username = 'admin';
UPDATE ranking_pairing_codes SET owner_username = 'Liam' WHERE owner_username = 'admin';
UPDATE ranking_point_adjustments SET owner_username = 'Liam' WHERE owner_username = 'admin';
UPDATE ranking_point_adjustments SET actor = 'Liam' WHERE actor = 'admin';
UPDATE ranking_reward_redemptions SET owner_username = 'Liam' WHERE owner_username = 'admin';
UPDATE ranking_reward_redemptions SET fulfilled_by = 'Liam' WHERE fulfilled_by = 'admin';
UPDATE ranking_rewards SET owner_username = 'Liam' WHERE owner_username = 'admin';
UPDATE ranking_student_accounts SET owner_username = 'Liam' WHERE owner_username = 'admin';
UPDATE ranking_students SET owner_username = 'Liam' WHERE owner_username = 'admin';
UPDATE ranking_teacher_mappings SET owner_username = 'Liam' WHERE owner_username = 'admin';
UPDATE exam_sessions SET created_by = 'Liam' WHERE created_by = 'admin';
UPDATE users
SET username = 'Liam', display_name = 'Liam', updated_at = CURRENT_TIMESTAMP(6)
WHERE username = 'admin' AND is_admin = TRUE;

SET FOREIGN_KEY_CHECKS = @previous_foreign_key_checks;
