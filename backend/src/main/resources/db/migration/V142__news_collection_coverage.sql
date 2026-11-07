ALTER TABLE news_sources ADD last_collection_stats LONGTEXT NULL;
ALTER TABLE news_articles ADD content_kind VARCHAR(12) NOT NULL DEFAULT 'article';
CREATE TABLE news_collection_schedule (
 id INT NOT NULL PRIMARY KEY,
 last_queued_at TIMESTAMP(6) NULL
);
INSERT INTO news_collection_schedule(id) VALUES(1);
