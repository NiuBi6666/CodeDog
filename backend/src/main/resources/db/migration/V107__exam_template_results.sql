ALTER TABLE exam_sessions ADD COLUMN result_mode VARCHAR(12) NOT NULL DEFAULT 'legacy';
ALTER TABLE exam_scores ADD COLUMN absent BIT(1) NOT NULL DEFAULT b'0';
