
CREATE TABLE ranking_reward_rules (
 reward_id BIGINT PRIMARY KEY,owner_username VARCHAR(50) NOT NULL,low_stock_threshold INT NOT NULL DEFAULT 3,
 per_student_limit INT NOT NULL DEFAULT 0,starts_at TIMESTAMP(6) NULL,ends_at TIMESTAMP(6) NULL,instructions VARCHAR(500) NOT NULL DEFAULT '',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT chk_reward_rules CHECK (low_stock_threshold>=0 AND per_student_limit>=0)
) ENGINE=InnoDB;
CREATE TABLE ranking_messages (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,owner_username VARCHAR(50) NOT NULL,recipient_kind VARCHAR(20) NOT NULL,recipient_id VARCHAR(100) NOT NULL,
 business_key VARCHAR(220) NOT NULL,title VARCHAR(100) NOT NULL,body VARCHAR(500) NOT NULL,target VARCHAR(50) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),read_at TIMESTAMP(6) NULL,
 UNIQUE KEY uk_message_business(owner_username,recipient_kind,recipient_id,business_key),
 KEY idx_messages_inbox(owner_username,recipient_kind,recipient_id,id)
) ENGINE=InnoDB;
