
CREATE TABLE lens_sources (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 source_name VARCHAR(80) NOT NULL, rss_url VARCHAR(1000) NOT NULL,
 category VARCHAR(30) NOT NULL, priority INT NOT NULL DEFAULT 80,
 refresh_interval_min INT NOT NULL DEFAULT 30, is_active BOOLEAN NOT NULL DEFAULT TRUE,
 last_crawled_at TIMESTAMP(6) NULL, last_success_at TIMESTAMP(6) NULL,
 lease_until TIMESTAMP(6) NULL, last_error VARCHAR(300) NULL
);
CREATE TABLE lens_articles (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 url_hash VARCHAR(64) NOT NULL UNIQUE, origin_url VARCHAR(2000) NOT NULL,
 source_id BIGINT NOT NULL, source_name VARCHAR(80) NOT NULL,
 category VARCHAR(30) NOT NULL, priority INT NOT NULL DEFAULT 80,
 author VARCHAR(200) NOT NULL DEFAULT '', cover_image VARCHAR(2000),
 title_en VARCHAR(1000) NOT NULL, title_zh VARCHAR(1000), summary_zh TEXT,
 content_en LONGTEXT NOT NULL, content_zh LONGTEXT,
 published_at TIMESTAMP(6) NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 status VARCHAR(30) NOT NULL DEFAULT 'pending', attempts INT NOT NULL DEFAULT 0,
 next_attempt_at TIMESTAMP(6) NULL, last_error VARCHAR(300), translated_at TIMESTAMP(6) NULL,
 event_id BIGINT NULL,
 INDEX idx_lens_status_time(status,published_at), INDEX idx_lens_retry(status,next_attempt_at)
);
CREATE TABLE lens_batches (
 batch_id VARCHAR(80) NOT NULL PRIMARY KEY, payload LONGTEXT NOT NULL,
 status VARCHAR(30) NOT NULL DEFAULT 'queued', created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 last_error VARCHAR(300)
);
CREATE TABLE lens_refresh_runs (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, status VARCHAR(30) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), last_error VARCHAR(300)
);
INSERT INTO lens_sources(source_name,rss_url,category,priority,refresh_interval_min) VALUES
 ('BBC News','https://feeds.bbci.co.uk/news/world/rss.xml','国际',95,30),
 ('The Guardian','https://www.theguardian.com/world/rss','国际',90,30),
 ('TechCrunch','https://techcrunch.com/feed/','科技',85,30),
 ('CNBC','https://www.cnbc.com/id/100003114/device/rss/rss.html','财经',85,30),
 ('EdSurge','https://www.edsurge.com/articles_rss','教育',80,120);
