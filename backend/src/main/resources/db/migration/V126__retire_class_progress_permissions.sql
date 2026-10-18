-- Remove grants for the retired classroom progress module only.
-- Keep historical audit records and previously applied migrations unchanged.
DELETE FROM user_permissions
WHERE permission_code IN (
    'class_progress.view', 'class_progress.import', 'class_progress.copy',
    'class_progress.export', 'class_progress.credential', 'class_progress.query'
);
