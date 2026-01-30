-- Migration to properly use CollectionKind enum
-- Sets the kind field based on existing collection names

-- Set existing collections with name 'likes' to LIKES kind
UPDATE collections
SET kind = 'LIKES'
WHERE name = 'likes';

-- Set existing collections with name 'favorites' to BOOKMARKS kind
UPDATE collections
SET kind = 'BOOKMARKS'
WHERE name = 'favorites';

-- Set all other collections to USER kind (or where kind is null)
UPDATE collections
SET kind = 'USER'
WHERE kind IS NULL OR (kind != 'LIKES' AND kind != 'BOOKMARKS');

-- Make kind NOT NULL now that all rows have values
ALTER TABLE collections
ALTER COLUMN kind SET NOT NULL;
