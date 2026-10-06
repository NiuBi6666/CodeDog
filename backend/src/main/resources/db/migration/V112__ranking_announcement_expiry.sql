ALTER TABLE ranking_announcements
  ADD COLUMN unpublish_at TIMESTAMP(6) NULL AFTER publish_at;

ALTER TABLE ranking_announcements
  ADD KEY idx_ranking_announcements_expiry (owner_username, enabled, unpublish_at);
