CREATE TABLE ranking_student_styles (
 owner_username VARCHAR(50) NOT NULL,
 student_id VARCHAR(100) NOT NULL,
 avatar_frame VARCHAR(24) NOT NULL DEFAULT 'base',
 title_code VARCHAR(24) NOT NULL DEFAULT 'rookie',
 background_code VARCHAR(24) NOT NULL DEFAULT 'grid',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(owner_username, student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
