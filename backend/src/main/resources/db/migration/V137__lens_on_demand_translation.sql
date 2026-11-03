
ALTER TABLE lens_articles ADD body_requested_at TIMESTAMP(6) NULL;
ALTER TABLE lens_articles ADD title_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE lens_articles ADD title_next_attempt_at TIMESTAMP(6) NULL;
ALTER TABLE lens_articles ADD title_lease_until TIMESTAMP(6) NULL;
CREATE INDEX idx_lens_body_demand ON lens_articles(status,body_requested_at,next_attempt_at);
