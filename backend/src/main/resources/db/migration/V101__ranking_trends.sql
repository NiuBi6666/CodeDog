ALTER TABLE ranking_students
  ADD COLUMN score_reached_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) AFTER student_name,
  ADD KEY idx_ranking_students_reached (camp_id, class_id, score_reached_at);

UPDATE ranking_students s
LEFT JOIN (
  SELECT camp_id, class_id, student_id, MAX(updated_at) AS reached_at
  FROM ranking_lesson_results
  GROUP BY camp_id, class_id, student_id
) r ON r.camp_id=s.camp_id AND r.class_id=s.class_id AND r.student_id=s.student_id
SET s.score_reached_at=COALESCE(r.reached_at, s.updated_at);

CREATE TABLE ranking_daily_snapshots (
  snapshot_date DATE NOT NULL,
  camp_id VARCHAR(100) NOT NULL,
  scope_type VARCHAR(10) NOT NULL,
  class_id VARCHAR(100) NOT NULL DEFAULT '',
  student_id VARCHAR(100) NOT NULL,
  rank_no INT NOT NULL,
  total_points INT NOT NULL,
  captured_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (snapshot_date, camp_id, scope_type, class_id, student_id),
  KEY idx_ranking_snapshot_baseline (camp_id, scope_type, class_id, snapshot_date),
  CONSTRAINT fk_ranking_snapshot_camp FOREIGN KEY (camp_id) REFERENCES ranking_camps(camp_id),
  CONSTRAINT chk_ranking_snapshot_scope CHECK (scope_type IN ('camp','class'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
