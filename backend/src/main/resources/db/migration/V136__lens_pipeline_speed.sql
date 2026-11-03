
CREATE TABLE lens_sync_gate (id INT NOT NULL PRIMARY KEY);
INSERT INTO lens_sync_gate(id) VALUES(1);
ALTER TABLE lens_refresh_runs ADD requested_by VARCHAR(200);
ALTER TABLE lens_refresh_runs ADD request_id VARCHAR(64);
ALTER TABLE lens_refresh_runs ADD request_ip VARCHAR(100);
ALTER TABLE lens_refresh_runs ADD lease_until TIMESTAMP(6) NULL;
ALTER TABLE lens_refresh_runs ADD collection_finished BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE lens_batches ADD lease_until TIMESTAMP(6) NULL;
CREATE TABLE lens_translation_cache (
 cache_key VARCHAR(64) NOT NULL PRIMARY KEY,
 engine VARCHAR(30) NOT NULL,
 translated_text LONGTEXT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
