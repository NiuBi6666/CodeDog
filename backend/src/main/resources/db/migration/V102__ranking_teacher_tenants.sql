ALTER TABLE users ADD COLUMN teacher_public_id VARCHAR(11) NULL AFTER username;
UPDATE users SET teacher_public_id=CONCAT('CD-', UPPER(SUBSTRING(SHA2(CONCAT('codedog:', id, ':', username), 256), 1, 8)));
ALTER TABLE users
  MODIFY teacher_public_id VARCHAR(11) NOT NULL,
  ADD UNIQUE KEY uk_users_teacher_public_id (teacher_public_id);

CREATE TABLE ranking_teacher_mappings (
  crm_teacher_id VARCHAR(100) NOT NULL PRIMARY KEY,
  owner_username VARCHAR(50) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_ranking_teacher_mapping_owner (owner_username),
  CONSTRAINT fk_ranking_teacher_mapping_owner FOREIGN KEY (owner_username) REFERENCES users(username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE ranking_lessons DROP FOREIGN KEY fk_ranking_lesson_class;
ALTER TABLE ranking_students DROP FOREIGN KEY fk_ranking_student_class;
ALTER TABLE ranking_classes DROP FOREIGN KEY fk_ranking_class_camp;
ALTER TABLE ranking_lesson_results DROP FOREIGN KEY fk_ranking_result_lesson;
ALTER TABLE ranking_lesson_results DROP FOREIGN KEY fk_ranking_result_student;
ALTER TABLE ranking_lesson_results DROP INDEX fk_ranking_result_student;
ALTER TABLE ranking_daily_snapshots DROP FOREIGN KEY fk_ranking_snapshot_camp;

ALTER TABLE ranking_camps ADD COLUMN owner_username VARCHAR(50) NULL FIRST;
ALTER TABLE ranking_classes ADD COLUMN owner_username VARCHAR(50) NULL FIRST;
ALTER TABLE ranking_lessons ADD COLUMN owner_username VARCHAR(50) NULL FIRST;
ALTER TABLE ranking_students ADD COLUMN owner_username VARCHAR(50) NULL FIRST;
ALTER TABLE ranking_import_batches ADD COLUMN owner_username VARCHAR(50) NULL AFTER id;
ALTER TABLE ranking_lesson_results ADD COLUMN owner_username VARCHAR(50) NULL FIRST;
ALTER TABLE ranking_point_adjustments ADD COLUMN owner_username VARCHAR(50) NULL AFTER id;
ALTER TABLE ranking_daily_snapshots ADD COLUMN owner_username VARCHAR(50) NULL AFTER snapshot_date;

UPDATE ranking_camps SET owner_username=(SELECT username FROM users ORDER BY is_admin DESC, id LIMIT 1);
UPDATE ranking_classes SET owner_username=(SELECT username FROM users ORDER BY is_admin DESC, id LIMIT 1);
UPDATE ranking_lessons SET owner_username=(SELECT username FROM users ORDER BY is_admin DESC, id LIMIT 1);
UPDATE ranking_students SET owner_username=(SELECT username FROM users ORDER BY is_admin DESC, id LIMIT 1);
UPDATE ranking_import_batches SET owner_username=(SELECT username FROM users ORDER BY is_admin DESC, id LIMIT 1);
UPDATE ranking_lesson_results SET owner_username=(SELECT username FROM users ORDER BY is_admin DESC, id LIMIT 1);
UPDATE ranking_point_adjustments SET owner_username=(SELECT username FROM users ORDER BY is_admin DESC, id LIMIT 1);
UPDATE ranking_daily_snapshots SET owner_username=(SELECT username FROM users ORDER BY is_admin DESC, id LIMIT 1);

ALTER TABLE ranking_camps MODIFY owner_username VARCHAR(50) NOT NULL;
ALTER TABLE ranking_classes MODIFY owner_username VARCHAR(50) NOT NULL;
ALTER TABLE ranking_lessons MODIFY owner_username VARCHAR(50) NOT NULL;
ALTER TABLE ranking_students MODIFY owner_username VARCHAR(50) NOT NULL;
ALTER TABLE ranking_import_batches MODIFY owner_username VARCHAR(50) NOT NULL;
ALTER TABLE ranking_lesson_results MODIFY owner_username VARCHAR(50) NOT NULL;
ALTER TABLE ranking_point_adjustments MODIFY owner_username VARCHAR(50) NOT NULL;
ALTER TABLE ranking_daily_snapshots MODIFY owner_username VARCHAR(50) NOT NULL;
ALTER TABLE ranking_extension_devices MODIFY owner_username VARCHAR(50) NOT NULL;
ALTER TABLE ranking_pairing_codes MODIFY owner_username VARCHAR(50) NOT NULL;

ALTER TABLE ranking_camps
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (owner_username, camp_id),
  ADD CONSTRAINT fk_ranking_camp_owner FOREIGN KEY (owner_username) REFERENCES users(username);
ALTER TABLE ranking_classes
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (owner_username, camp_id, class_id),
  ADD CONSTRAINT fk_ranking_class_camp FOREIGN KEY (owner_username, camp_id) REFERENCES ranking_camps(owner_username, camp_id);
ALTER TABLE ranking_lessons
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (owner_username, camp_id, class_id, lesson_id),
  ADD CONSTRAINT fk_ranking_lesson_class FOREIGN KEY (owner_username, camp_id, class_id) REFERENCES ranking_classes(owner_username, camp_id, class_id);
ALTER TABLE ranking_students
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (owner_username, camp_id, class_id, student_id),
  ADD CONSTRAINT fk_ranking_student_class FOREIGN KEY (owner_username, camp_id, class_id) REFERENCES ranking_classes(owner_username, camp_id, class_id);
ALTER TABLE ranking_lesson_results
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (owner_username, camp_id, class_id, lesson_id, student_id),
  ADD CONSTRAINT fk_ranking_result_lesson FOREIGN KEY (owner_username, camp_id, class_id, lesson_id) REFERENCES ranking_lessons(owner_username, camp_id, class_id, lesson_id),
  ADD CONSTRAINT fk_ranking_result_student FOREIGN KEY (owner_username, camp_id, class_id, student_id) REFERENCES ranking_students(owner_username, camp_id, class_id, student_id);
ALTER TABLE ranking_daily_snapshots
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (snapshot_date, owner_username, camp_id, scope_type, class_id, student_id),
  ADD CONSTRAINT fk_ranking_snapshot_camp FOREIGN KEY (owner_username, camp_id) REFERENCES ranking_camps(owner_username, camp_id);

ALTER TABLE ranking_import_batches
  ADD KEY idx_ranking_batches_owner_camp_created (owner_username, camp_id, created_at);
ALTER TABLE ranking_point_adjustments
  ADD KEY idx_ranking_adjustments_owner_student (owner_username, camp_id, class_id, student_id);
ALTER TABLE ranking_extension_devices
  ADD CONSTRAINT fk_ranking_device_owner FOREIGN KEY (owner_username) REFERENCES users(username);
ALTER TABLE ranking_pairing_codes
  ADD CONSTRAINT fk_ranking_pairing_owner FOREIGN KEY (owner_username) REFERENCES users(username);
