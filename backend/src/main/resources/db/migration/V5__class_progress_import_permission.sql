INSERT IGNORE INTO user_permissions(user_id, permission_code)
SELECT DISTINCT user_id, 'class_progress.import'
FROM user_permissions
WHERE permission_code IN ('class_progress.credential', 'class_progress.query');

DELETE FROM user_permissions
WHERE permission_code IN ('class_progress.credential', 'class_progress.query');
