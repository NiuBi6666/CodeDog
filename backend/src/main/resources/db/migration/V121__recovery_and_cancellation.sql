ALTER TABLE ranking_student_accounts ADD COLUMN session_version INT NOT NULL DEFAULT 0, ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ranking_rewards ADD COLUMN deleted_at TIMESTAMP(6) NULL;
CREATE TABLE ranking_recovery_requests (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,owner_username VARCHAR(50) NOT NULL,student_id VARCHAR(100) NOT NULL,
 student_name VARCHAR(100) NOT NULL,status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFY',
 reason VARCHAR(500) NOT NULL,verification_note VARCHAR(500) NULL,verified_by VARCHAR(100) NULL,
 resolved_by VARCHAR(100) NULL,created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 KEY idx_recovery_owner(owner_username,status,created_at)
) ENGINE=InnoDB;
CREATE TABLE ranking_redemption_cancellations (
 redemption_id BIGINT PRIMARY KEY,owner_username VARCHAR(50) NOT NULL,student_id VARCHAR(100) NOT NULL,
 reason VARCHAR(500) NOT NULL,decision_note VARCHAR(500) NULL,decided_by VARCHAR(100) NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),decided_at TIMESTAMP(6) NULL
) ENGINE=InnoDB;

ALTER TABLE ranking_reward_redemptions ADD COLUMN request_key VARCHAR(80) NULL, ADD UNIQUE KEY uk_redemption_request(owner_username,student_id,request_key);
