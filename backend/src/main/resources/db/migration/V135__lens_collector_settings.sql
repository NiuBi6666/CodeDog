CREATE TABLE lens_collector_settings (
 id INT NOT NULL PRIMARY KEY,
 repository VARCHAR(200) NOT NULL, workflow VARCHAR(100) NOT NULL,
 api_url VARCHAR(500) NOT NULL,
 github_credential TEXT NOT NULL, webhook_credential TEXT NOT NULL,
 setup_state VARCHAR(30) NOT NULL DEFAULT 'saved', setup_step VARCHAR(200),
 last_error VARCHAR(500), lease_until TIMESTAMP(6) NULL,
 requested_by VARCHAR(100), request_ip VARCHAR(100), request_id VARCHAR(64),
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), last_success_at TIMESTAMP(6) NULL
);
