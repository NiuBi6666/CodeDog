CREATE TABLE IF NOT EXISTS news_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 title VARCHAR(300) NOT NULL,
 category VARCHAR(30) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'draft',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
CREATE TABLE IF NOT EXISTS news_event_nodes (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 event_id BIGINT NOT NULL,
 title VARCHAR(1000) NOT NULL,
 occurred_at TIMESTAMP(6) NOT NULL,
 time_basis VARCHAR(20) NOT NULL DEFAULT 'published',
 stage VARCHAR(20) NOT NULL DEFAULT 'development',
 INDEX idx_event_node_time(event_id,occurred_at,id)
);
CREATE TABLE IF NOT EXISTS news_event_articles (
 event_id BIGINT NOT NULL,
 article_id BIGINT NOT NULL,
 node_id BIGINT,
 state VARCHAR(20) NOT NULL DEFAULT 'suggested',
 confidence DOUBLE NOT NULL DEFAULT 0,
 method VARCHAR(20) NOT NULL DEFAULT 'manual',
 PRIMARY KEY(event_id,article_id),
 INDEX idx_event_article(article_id,state)
);
CREATE TABLE IF NOT EXISTS news_event_scan (
 id INT PRIMARY KEY,
 last_article_id BIGINT NOT NULL DEFAULT 0
);
INSERT INTO news_event_scan(id,last_article_id)
 SELECT 1,0 WHERE NOT EXISTS(SELECT 1 FROM news_event_scan WHERE id=1);

CREATE TABLE IF NOT EXISTS news_event_scanned_articles (article_id BIGINT PRIMARY KEY);
