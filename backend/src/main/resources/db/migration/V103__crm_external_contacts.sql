CREATE TABLE crm_external_contacts (
  owner_username VARCHAR(50) NOT NULL,
  crm_user_id VARCHAR(100) NOT NULL,
  external_userid VARCHAR(128) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (owner_username, crm_user_id),
  KEY idx_crm_external_contacts_external (owner_username, external_userid),
  CONSTRAINT fk_crm_external_contacts_owner
    FOREIGN KEY (owner_username) REFERENCES users(username)
);
