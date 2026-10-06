CREATE TABLE ranking_student_accounts (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  phone VARCHAR(20) NOT NULL,
  owner_username VARCHAR(50) NOT NULL,
  student_id VARCHAR(100) NOT NULL,
  student_name VARCHAR(100) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_ranking_student_account_phone (phone),
  UNIQUE KEY uk_ranking_student_account_owner_student (owner_username, student_id),
  KEY idx_ranking_student_account_student (owner_username, student_id),
  CONSTRAINT fk_ranking_student_account_owner FOREIGN KEY (owner_username) REFERENCES users(username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO ranking_student_accounts(phone, owner_username, student_id, student_name, password_hash, enabled)
VALUES (
  '15801493928', 'admin', '6105387', '张睿宸',
  '$2a$12$/5u2DSB1/7n7/yYRrfzw.uRIrU6wdvbOhOCb2ax85lvo4yZMsPX2.', TRUE
)
ON DUPLICATE KEY UPDATE student_name=VALUES(student_name), enabled=TRUE, updated_at=CURRENT_TIMESTAMP(6);
