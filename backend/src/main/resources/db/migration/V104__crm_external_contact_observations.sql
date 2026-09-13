CREATE TABLE crm_external_contact_observations (
  owner_username VARCHAR(50) NOT NULL,
  crm_user_id VARCHAR(100) NOT NULL,
  external_userid VARCHAR(128) NOT NULL,
  first_seen_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  last_seen_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  seen_count BIGINT UNSIGNED NOT NULL DEFAULT 1,
  PRIMARY KEY (owner_username, crm_user_id, external_userid),
  KEY idx_crm_external_contact_observations_external (owner_username, external_userid),
  CONSTRAINT fk_crm_external_contact_observations_owner
    FOREIGN KEY (owner_username) REFERENCES users(username)
);

INSERT INTO crm_external_contact_observations
  (owner_username, crm_user_id, external_userid, first_seen_at, last_seen_at, seen_count)
SELECT owner_username, crm_user_id, external_userid, created_at, updated_at, 1
FROM crm_external_contacts;
