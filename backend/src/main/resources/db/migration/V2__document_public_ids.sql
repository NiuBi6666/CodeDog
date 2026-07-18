ALTER TABLE documents ADD COLUMN public_id VARCHAR(8) NULL AFTER id;

UPDATE documents
SET public_id = LOWER(LEFT(SHA2(CONCAT(UUID(), ':', id), 256), 8))
WHERE public_id IS NULL;

ALTER TABLE documents
    MODIFY public_id VARCHAR(8) NOT NULL,
    ADD UNIQUE KEY uk_documents_public_id (public_id);
