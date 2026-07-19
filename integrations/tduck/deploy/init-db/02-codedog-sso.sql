CREATE TABLE IF NOT EXISTS codedog_sso_identity (
  codedog_subject VARCHAR(64) NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  email VARCHAR(100) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (codedog_subject),
  UNIQUE KEY uk_codedog_sso_user (user_id),
  UNIQUE KEY uk_codedog_sso_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS codedog_sso_nonce (
  nonce CHAR(36) NOT NULL,
  expires_at DATETIME NOT NULL,
  PRIMARY KEY (nonce),
  KEY idx_codedog_sso_nonce_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
