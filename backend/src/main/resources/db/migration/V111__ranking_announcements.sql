CREATE TABLE ranking_announcements (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  owner_username VARCHAR(50) NOT NULL,
  announcement_text VARCHAR(500) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT FALSE,
  publish_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  KEY idx_ranking_announcements_owner_time (owner_username, created_at, id),
  KEY idx_ranking_announcements_public (owner_username, enabled, publish_at),
  CONSTRAINT fk_ranking_announcements_owner FOREIGN KEY (owner_username) REFERENCES users(username),
  CONSTRAINT chk_ranking_announcements_text CHECK (CHAR_LENGTH(announcement_text) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO ranking_announcements(owner_username, announcement_text, enabled, publish_at)
SELECT owner_username, announcement, TRUE, CURRENT_TIMESTAMP(6)
FROM ranking_board_settings
WHERE TRIM(announcement) <> '';
