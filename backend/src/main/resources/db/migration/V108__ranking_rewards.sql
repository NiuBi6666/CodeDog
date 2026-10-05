CREATE TABLE ranking_board_settings (
  owner_username VARCHAR(50) NOT NULL PRIMARY KEY,
  announcement VARCHAR(500) NOT NULL DEFAULT '',
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_ranking_settings_owner FOREIGN KEY (owner_username) REFERENCES users(username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ranking_rewards (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  owner_username VARCHAR(50) NOT NULL,
  reward_name VARCHAR(100) NOT NULL,
  required_points INT NOT NULL,
  image_data MEDIUMBLOB NULL,
  image_content_type VARCHAR(50) NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  KEY idx_ranking_rewards_owner_enabled (owner_username, enabled, required_points),
  CONSTRAINT fk_ranking_rewards_owner FOREIGN KEY (owner_username) REFERENCES users(username),
  CONSTRAINT chk_ranking_rewards_points CHECK (required_points > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ranking_reward_redemptions (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  owner_username VARCHAR(50) NOT NULL,
  student_id VARCHAR(100) NOT NULL,
  student_name VARCHAR(100) NOT NULL,
  reward_id BIGINT NULL,
  reward_name VARCHAR(100) NOT NULL,
  points_spent INT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  redeemed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  fulfilled_at TIMESTAMP(6) NULL,
  fulfilled_by VARCHAR(50) NULL,
  KEY idx_ranking_redemptions_owner_time (owner_username, redeemed_at),
  KEY idx_ranking_redemptions_owner_status (owner_username, status, redeemed_at),
  KEY idx_ranking_redemptions_student (owner_username, student_id),
  KEY idx_ranking_redemptions_reward (reward_id),
  CONSTRAINT fk_ranking_redemptions_owner FOREIGN KEY (owner_username) REFERENCES users(username),
  CONSTRAINT fk_ranking_redemptions_reward FOREIGN KEY (reward_id) REFERENCES ranking_rewards(id) ON DELETE SET NULL,
  CONSTRAINT chk_ranking_redemptions_points CHECK (points_spent > 0),
  CONSTRAINT chk_ranking_redemptions_status CHECK (status IN ('PENDING','FULFILLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
