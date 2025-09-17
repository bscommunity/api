-- Migration: Remove interactions table and migrate data to collections system
-- This migration removes the interactions table since we now use collections for everything

-- First, migrate existing liked content to "Likes" collections
-- This is a data migration that should be run carefully
DO $$
DECLARE
    user_record RECORD;
    likes_collection_id BIGINT;
BEGIN
    -- For each user that has liked content, create a "Likes" collection
    FOR user_record IN
        SELECT DISTINCT user_id
        FROM interactions
        WHERE liked_at IS NOT NULL
    LOOP
        -- Create "Likes" collection for this user
        INSERT INTO collections (user_id, name, is_public, created_at, updated_at)
        VALUES (user_record.user_id, 'Likes', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        RETURNING id INTO likes_collection_id;

        -- Migrate all liked content to the collection
        INSERT INTO collection_items (collection_id, chart_id, tour_pass_id, theme_id, added_at)
        SELECT
            likes_collection_id,
            chart_id,
            tour_pass_id,
            theme_id,
            liked_at
        FROM interactions
        WHERE user_id = user_record.user_id
        AND liked_at IS NOT NULL;
    END LOOP;

    RAISE NOTICE 'Migrated liked content to collections system';
END $$;

-- Drop the interactions table since it's no longer needed
DROP TABLE IF EXISTS interactions CASCADE;

-- Create indexes for better performance on system collections
CREATE INDEX idx_collections_system_name ON collections(name) WHERE name IN ('Likes', 'Favorites');
CREATE INDEX idx_collections_user_system ON collections(user_id, name) WHERE name IN ('Likes', 'Favorites');
