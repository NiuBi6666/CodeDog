CREATE TABLE ranking_recovery_drills (
 id VARCHAR(36) PRIMARY KEY,backup_name VARCHAR(150) NOT NULL,started_at TIMESTAMP(6) NOT NULL,finished_at TIMESTAMP(6) NOT NULL,
 recovery_point TIMESTAMP(6) NOT NULL,report_json LONGTEXT NOT NULL,executed_by VARCHAR(50) NOT NULL,
 KEY idx_drill_finished(finished_at)
) ENGINE=InnoDB;
