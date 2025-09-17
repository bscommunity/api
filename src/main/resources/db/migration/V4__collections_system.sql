-- Migration: Add Collections system to replace favorites
-- This migration creates the collections and collection_items tables
-- and removes the favorited_at column from interactions

-- Create collections table
CREATE TABLE collections (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(30) NOT NULL,
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, name)
);

-- Create indexes for collections
CREATE INDEX idx_collections_user_id ON collections(user_id);
CREATE INDEX idx_collections_user_id_name ON collections(user_id, name);

-- Create collection_items table
CREATE TABLE collection_items (
    id SERIAL PRIMARY KEY,
    collection_id BIGINT NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
    chart_id BIGINT REFERENCES charts(id) ON DELETE CASCADE,
    tour_pass_id BIGINT REFERENCES tour_passes(id) ON DELETE CASCADE,
    theme_id BIGINT REFERENCES themes(id) ON DELETE CASCADE,
    added_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_collection_items_at_least_one_target CHECK (
        (chart_id IS NOT NULL)::int +
        (tour_pass_id IS NOT NULL)::int +
        (theme_id IS NOT NULL)::int = 1
    )
);

-- Create indexes for collection_items
CREATE INDEX idx_collection_items_collection_id ON collection_items(collection_id);
CREATE INDEX idx_collection_items_chart_id ON collection_items(chart_id);
CREATE INDEX idx_collection_items_tour_pass_id ON collection_items(tour_pass_id);
CREATE INDEX idx_collection_items_theme_id ON collection_items(theme_id);

-- Create unique indexes to prevent duplicates
CREATE UNIQUE INDEX idx_collection_items_collection_chart ON collection_items(collection_id, chart_id) WHERE chart_id IS NOT NULL;
CREATE UNIQUE INDEX idx_collection_items_collection_tour_pass ON collection_items(collection_id, tour_pass_id) WHERE tour_pass_id IS NOT NULL;
CREATE UNIQUE INDEX idx_collection_items_collection_theme ON collection_items(collection_id, theme_id) WHERE theme_id IS NOT NULL;

-- Remove favorited_at column from interactions table (if it exists)
-- This is done conditionally to avoid errors if the column doesn't exist
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'interactions'
        AND column_name = 'favorited_at'
    ) THEN
        ALTER TABLE interactions DROP COLUMN favorited_at;
    END IF;
END $$;
