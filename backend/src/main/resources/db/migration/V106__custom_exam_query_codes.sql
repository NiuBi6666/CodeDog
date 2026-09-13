ALTER TABLE exam_sessions
 ADD COLUMN query_code VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NULL,
 ADD UNIQUE KEY uk_exam_query_code(query_code);

CREATE TABLE exam_query_aliases (
 code VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 exam_id BIGINT NOT NULL,
 KEY idx_exam_alias_session(exam_id),
 CONSTRAINT fk_exam_alias_session FOREIGN KEY(exam_id) REFERENCES exam_sessions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
