CREATE TABLE ranking_student_graduations (
 owner_username VARCHAR(50) NOT NULL,student_id VARCHAR(100) NOT NULL,
 graduated_at TIMESTAMP(6) NULL,updated_by VARCHAR(50) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY (owner_username,student_id),
 KEY idx_graduation_owner_time (owner_username,graduated_at)
) ENGINE=InnoDB;
