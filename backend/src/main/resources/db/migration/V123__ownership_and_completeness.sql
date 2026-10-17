
ALTER TABLE users ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE ranking_student_accounts DROP INDEX uk_ranking_student_account_phone;
CREATE INDEX idx_student_phone ON ranking_student_accounts(phone);
CREATE TABLE ranking_current_ownership (
 student_id VARCHAR(100) PRIMARY KEY,owner_username VARCHAR(50) NULL,state VARCHAR(20) NOT NULL,version_no BIGINT NOT NULL DEFAULT 1,
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;
INSERT INTO ranking_current_ownership(student_id,owner_username,state)
 SELECT student_id,CASE WHEN COUNT(DISTINCT owner_username)=1 THEN MIN(owner_username) ELSE NULL END,
 CASE WHEN COUNT(DISTINCT owner_username)=1 THEN 'ACTIVE' ELSE 'CONFLICT' END FROM ranking_students GROUP BY student_id;
CREATE TABLE ranking_transfer_previews (
 id VARCHAR(36) PRIMARY KEY,payload_json LONGTEXT NOT NULL,created_by VARCHAR(50) NOT NULL,
 expires_at TIMESTAMP(6) NOT NULL,confirmed_at TIMESTAMP(6) NULL
) ENGINE=InnoDB;
CREATE TABLE ranking_transfers (
 id VARCHAR(36) PRIMARY KEY,student_id VARCHAR(100) NOT NULL,from_owner VARCHAR(50) NOT NULL,to_owner VARCHAR(50) NOT NULL,
 points BIGINT NOT NULL,reason VARCHAR(255) NOT NULL,actor VARCHAR(50) NOT NULL,created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 KEY idx_transfer_student(student_id,created_at)
) ENGINE=InnoDB;
CREATE TABLE ranking_expected_lessons (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,owner_username VARCHAR(50) NOT NULL,camp_id VARCHAR(100) NOT NULL,class_id VARCHAR(100) NOT NULL,
 lesson_id VARCHAR(100) NOT NULL,lesson_name VARCHAR(200) NOT NULL,planned_at TIMESTAMP(6) NOT NULL,
 UNIQUE KEY uk_expected_lesson(owner_username,camp_id,class_id,lesson_id)
) ENGINE=InnoDB;
