-- Auditing columns: who created/updated a row and optimistic locking version.
ALTER TABLE categories ADD COLUMN created_by VARCHAR(100);
ALTER TABLE categories ADD COLUMN updated_by VARCHAR(100);
ALTER TABLE categories ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE categories
SET created_by = 'system',
    updated_by = 'system'
WHERE created_by IS NULL;

ALTER TABLE categories ALTER COLUMN created_by SET NOT NULL;
ALTER TABLE categories ALTER COLUMN updated_by SET NOT NULL;

ALTER TABLE products ADD COLUMN created_by VARCHAR(100);
ALTER TABLE products ADD COLUMN updated_by VARCHAR(100);
ALTER TABLE products ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE products
SET created_by = 'system',
    updated_by = 'system'
WHERE created_by IS NULL;

ALTER TABLE products ALTER COLUMN created_by SET NOT NULL;
ALTER TABLE products ALTER COLUMN updated_by SET NOT NULL;
