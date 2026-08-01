CREATE TABLE ranking_rule_versions (
  version_no INT NOT NULL PRIMARY KEY, name VARCHAR(100) NOT NULL,
  completion_weight INT NOT NULL, inclass_weight INT NOT NULL, homework_weight INT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO ranking_rule_versions VALUES (1, '每课300分', 100, 100, 100, CURRENT_TIMESTAMP(6));

CREATE TABLE ranking_camps (
  camp_id VARCHAR(100) NOT NULL PRIMARY KEY, camp_name VARCHAR(160) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE ranking_classes (
  camp_id VARCHAR(100) NOT NULL, class_id VARCHAR(100) NOT NULL, class_name VARCHAR(160) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), PRIMARY KEY (camp_id, class_id),
  CONSTRAINT fk_ranking_class_camp FOREIGN KEY (camp_id) REFERENCES ranking_camps(camp_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE ranking_lessons (
  camp_id VARCHAR(100) NOT NULL, class_id VARCHAR(100) NOT NULL, lesson_id VARCHAR(100) NOT NULL,
  lesson_name VARCHAR(200) NOT NULL, lesson_order INT NULL, ended_at TIMESTAMP(6) NULL,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), PRIMARY KEY (camp_id, class_id, lesson_id),
  CONSTRAINT fk_ranking_lesson_class FOREIGN KEY (camp_id, class_id) REFERENCES ranking_classes(camp_id, class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE ranking_students (
  camp_id VARCHAR(100) NOT NULL, class_id VARCHAR(100) NOT NULL, student_id VARCHAR(100) NOT NULL,
  student_name VARCHAR(100) NOT NULL, updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (camp_id, class_id, student_id), KEY idx_ranking_students_name (student_name),
  CONSTRAINT fk_ranking_student_class FOREIGN KEY (camp_id, class_id) REFERENCES ranking_classes(camp_id, class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE ranking_import_batches (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, source_type VARCHAR(20) NOT NULL,
  source_name VARCHAR(160) NOT NULL, camp_id VARCHAR(100) NOT NULL, status VARCHAR(20) NOT NULL,
  received_rows INT NOT NULL DEFAULT 0, changed_rows INT NOT NULL DEFAULT 0,
  rejected_rows INT NOT NULL DEFAULT 0, actor VARCHAR(100) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), completed_at TIMESTAMP(6) NULL,
  KEY idx_ranking_batches_camp_created (camp_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE ranking_lesson_results (
  camp_id VARCHAR(100) NOT NULL, class_id VARCHAR(100) NOT NULL, lesson_id VARCHAR(100) NOT NULL,
  student_id VARCHAR(100) NOT NULL, completion_rate DECIMAL(6,5) NOT NULL,
  inclass_total INT NOT NULL, inclass_submitted INT NOT NULL, inclass_passed INT NOT NULL,
  homework_total INT NOT NULL, homework_submitted INT NOT NULL, homework_passed INT NOT NULL,
  completion_points INT NOT NULL, inclass_points INT NOT NULL, homework_points INT NOT NULL,
  total_points INT NOT NULL, rule_version INT NOT NULL, content_hash CHAR(64) NOT NULL,
  import_batch_id BIGINT NOT NULL, updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (camp_id, class_id, lesson_id, student_id),
  KEY idx_ranking_results_student (camp_id, student_id),
  KEY idx_ranking_results_points (camp_id, class_id, total_points),
  CONSTRAINT fk_ranking_result_lesson FOREIGN KEY (camp_id, class_id, lesson_id) REFERENCES ranking_lessons(camp_id, class_id, lesson_id),
  CONSTRAINT fk_ranking_result_student FOREIGN KEY (camp_id, class_id, student_id) REFERENCES ranking_students(camp_id, class_id, student_id),
  CONSTRAINT fk_ranking_result_batch FOREIGN KEY (import_batch_id) REFERENCES ranking_import_batches(id),
  CONSTRAINT fk_ranking_result_rule FOREIGN KEY (rule_version) REFERENCES ranking_rule_versions(version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE ranking_point_adjustments (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, camp_id VARCHAR(100) NOT NULL, class_id VARCHAR(100) NOT NULL,
  student_id VARCHAR(100) NOT NULL, points INT NOT NULL, reason VARCHAR(255) NOT NULL,
  actor VARCHAR(100) NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY idx_ranking_adjustments_student (camp_id, class_id, student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE ranking_pairing_codes (
  code_hash CHAR(64) NOT NULL PRIMARY KEY, owner_username VARCHAR(100) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL, used_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), KEY idx_ranking_pairing_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE ranking_extension_devices (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, token_hash CHAR(64) NOT NULL,
  owner_username VARCHAR(100) NOT NULL, device_name VARCHAR(100) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), last_seen_at TIMESTAMP(6) NULL,
  revoked_at TIMESTAMP(6) NULL, UNIQUE KEY uk_ranking_device_token (token_hash),
  KEY idx_ranking_device_owner (owner_username, revoked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
