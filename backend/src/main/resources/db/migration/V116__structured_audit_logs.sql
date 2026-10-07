ALTER TABLE audit_log
  ADD COLUMN owner_username VARCHAR(50) NULL AFTER action,
  ADD COLUMN actor_type VARCHAR(24) NULL AFTER owner_username,
  ADD COLUMN actor_id VARCHAR(100) NULL AFTER actor_type,
  ADD COLUMN actor_name VARCHAR(100) NULL AFTER actor_id,
  ADD COLUMN request_id VARCHAR(64) NULL AFTER actor_name,
  ADD COLUMN http_method VARCHAR(12) NULL AFTER request_id,
  ADD COLUMN request_path VARCHAR(500) NULL AFTER http_method,
  ADD COLUMN query_json LONGTEXT NULL AFTER request_path,
  ADD COLUMN status_code INT NULL AFTER query_json,
  ADD COLUMN duration_ms BIGINT NULL AFTER status_code,
  ADD COLUMN module VARCHAR(50) NULL AFTER duration_ms,
  ADD COLUMN event_type VARCHAR(30) NULL AFTER module,
  ADD COLUMN event_code VARCHAR(120) NULL AFTER event_type,
  ADD COLUMN result VARCHAR(20) NULL AFTER event_code,
  ADD COLUMN target_type VARCHAR(50) NULL AFTER result,
  ADD COLUMN target_id VARCHAR(120) NULL AFTER target_type,
  ADD COLUMN changes_json LONGTEXT NULL AFTER target_id,
  ADD COLUMN detail_json LONGTEXT NULL AFTER changes_json,
  ADD COLUMN error_message TEXT NULL AFTER detail_json,
  ADD COLUMN user_agent VARCHAR(500) NULL AFTER error_message;

UPDATE audit_log SET
  request_id=CONCAT('legacy-',id), actor_type='UNKNOWN', actor_name='未知',
  module=CASE
    WHEN action LIKE 'login_%' OR action LIKE 'registration_%' THEN 'auth'
    WHEN action LIKE 'password_%' OR action LIKE 'permissions_%' OR action LIKE 'crm_teacher_%' THEN 'account'
    WHEN action LIKE 'document_%' THEN 'documents'
    WHEN action LIKE 'student_%' THEN 'students'
    WHEN action LIKE 'class_progress_%' THEN 'classes'
    WHEN action LIKE 'ranking_%' THEN 'rankings'
    WHEN action LIKE 'exam_%' THEN 'exams'
    ELSE 'system' END,
  event_type='legacy', event_code=SUBSTRING_INDEX(action,':',1),
  result=CASE WHEN action LIKE '%_failed%' THEN 'failed' ELSE 'success' END
WHERE request_id IS NULL;

ALTER TABLE audit_log MODIFY request_id VARCHAR(64) NOT NULL;
CREATE UNIQUE INDEX idx_audit_request_id ON audit_log(request_id);
CREATE INDEX idx_audit_owner_created ON audit_log(owner_username,created_at);
CREATE INDEX idx_audit_module_created ON audit_log(module,created_at);
CREATE INDEX idx_audit_actor_created ON audit_log(actor_type,actor_id,created_at);
CREATE INDEX idx_audit_result_created ON audit_log(result,created_at);
CREATE INDEX idx_audit_target ON audit_log(target_type,target_id);
