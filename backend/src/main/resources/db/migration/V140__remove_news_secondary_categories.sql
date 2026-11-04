-- Remove the retired secondary-category feature. Article text, translations and primary categories remain intact.
DROP INDEX idx_news_category_subtag ON news_articles;
ALTER TABLE news_sources DROP COLUMN sub_tag;
ALTER TABLE news_articles DROP COLUMN sub_tag;
