CREATE TABLE ranking_student_honor_progress (
 owner_username VARCHAR(50) NOT NULL,
 student_id VARCHAR(100) NOT NULL,
 highest_announced_tier INT NOT NULL DEFAULT 1,
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(owner_username,student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
