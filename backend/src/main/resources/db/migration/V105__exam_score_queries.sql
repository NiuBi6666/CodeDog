CREATE TABLE exam_sessions (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 public_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 title VARCHAR(120) NOT NULL,
 score_labels TEXT NOT NULL,
 student_count INT NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 created_by VARCHAR(50) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_exam_public_id(public_id),
 KEY idx_exam_created(created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE exam_scores (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 exam_id BIGINT NOT NULL,
 student_name VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
 score_values TEXT NOT NULL,
 UNIQUE KEY uk_exam_student(exam_id,student_name),
 CONSTRAINT fk_exam_score_session FOREIGN KEY(exam_id) REFERENCES exam_sessions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
