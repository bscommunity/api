-- Drop the changelogs table as changelogs are now tied to versions via catalog_item_versions.changelog
ALTER TABLE changelogs DROP CONSTRAINT IF EXISTS fk_changelogs_catalog_item_id__id;
DROP TABLE IF EXISTS changelogs;
