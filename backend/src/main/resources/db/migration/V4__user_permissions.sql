ALTER TABLE users
    ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT FALSE AFTER password_hash,
    ADD COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) AFTER is_admin;

UPDATE users
SET is_admin = TRUE
WHERE id = (SELECT first_user_id FROM (SELECT MIN(id) AS first_user_id FROM users) existing_users);

CREATE TABLE user_permissions (
    user_id BIGINT NOT NULL,
    permission_code VARCHAR(80) NOT NULL,
    granted_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, permission_code),
    CONSTRAINT fk_user_permissions_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO user_permissions(user_id, permission_code)
SELECT id, 'dashboard.view'
FROM users
WHERE is_admin = FALSE;
