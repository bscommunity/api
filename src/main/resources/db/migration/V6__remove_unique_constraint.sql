DROP INDEX IF EXISTS collections_user_id_name;
ALTER TABLE IF EXISTS collections DROP CONSTRAINT IF EXISTS collections_user_id_kind_unique;
DROP INDEX IF EXISTS collections_user_id;
