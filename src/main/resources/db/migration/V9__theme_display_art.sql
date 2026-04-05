ALTER TABLE themes ADD COLUMN IF NOT EXISTS display_art_url VARCHAR(255);
UPDATE themes SET display_art_url = cover_url WHERE display_art_url IS NULL;
