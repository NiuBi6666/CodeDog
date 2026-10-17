CREATE TABLE ranking_point_ledger (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, owner_username VARCHAR(50) NOT NULL, student_id VARCHAR(100) NOT NULL,
 event_type VARCHAR(40) NOT NULL, business_key VARCHAR(220) NOT NULL, delta BIGINT NOT NULL,
 balance_before BIGINT NOT NULL, balance_after BIGINT NOT NULL, reason VARCHAR(500) NOT NULL, actor VARCHAR(100) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_ledger_business(owner_username,business_key), KEY idx_ledger_student(owner_username,student_id,id)
) ENGINE=InnoDB;
INSERT INTO ranking_point_ledger(owner_username,student_id,event_type,business_key,delta,balance_before,balance_after,reason,actor)
SELECT s.owner_username,s.student_id,'OPENING',CONCAT('opening:',s.student_id),
 COALESCE(e.points,0)+COALESCE(a.points,0)-COALESCE(d.points,0),0,
 COALESCE(e.points,0)+COALESCE(a.points,0)-COALESCE(d.points,0),'历史期初余额；详细来源保留在原业务记录','SYSTEM'
FROM (SELECT DISTINCT owner_username,student_id FROM ranking_students) s
LEFT JOIN (SELECT owner_username,student_id,SUM(total_points) points FROM ranking_lesson_results GROUP BY owner_username,student_id) e ON e.owner_username=s.owner_username AND e.student_id=s.student_id
LEFT JOIN (SELECT owner_username,student_id,SUM(points) points FROM ranking_point_adjustments GROUP BY owner_username,student_id) a ON a.owner_username=s.owner_username AND a.student_id=s.student_id
LEFT JOIN (SELECT owner_username,student_id,SUM(points_spent) points FROM ranking_reward_redemptions GROUP BY owner_username,student_id) d ON d.owner_username=s.owner_username AND d.student_id=s.student_id;
CREATE TABLE ranking_reconciliations (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,owner_username VARCHAR(50) NOT NULL,student_id VARCHAR(100) NOT NULL,
 source_balance BIGINT NOT NULL,ledger_balance BIGINT NOT NULL, result VARCHAR(20) NOT NULL,
 checked_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), KEY idx_reconcile(owner_username,checked_at)
) ENGINE=InnoDB;
CREATE TABLE ranking_import_previews (
 id VARCHAR(36) PRIMARY KEY,owner_username VARCHAR(50) NOT NULL,payload_json LONGTEXT NOT NULL,
 fingerprint CHAR(64) NOT NULL,expires_at TIMESTAMP(6) NOT NULL,confirmed_batch_id BIGINT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;
CREATE TABLE ranking_import_changes (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,batch_id BIGINT NOT NULL,owner_username VARCHAR(50) NOT NULL,
 camp_id VARCHAR(100) NOT NULL,class_id VARCHAR(100) NOT NULL,lesson_id VARCHAR(100) NOT NULL,student_id VARCHAR(100) NOT NULL,
 before_json LONGTEXT NULL,after_hash CHAR(64) NOT NULL, KEY idx_import_changes(owner_username,batch_id)
) ENGINE=InnoDB;
ALTER TABLE ranking_import_batches ADD COLUMN reversed_at TIMESTAMP(6) NULL;
