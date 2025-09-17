-- Create themes table
CREATE TABLE IF NOT EXISTS themes (
    id BIGSERIAL PRIMARY KEY,
    "shareId" VARCHAR(11) NOT NULL UNIQUE,
    cover_url VARCHAR(255) NOT NULL,
    is_public BOOLEAN NOT NULL DEFAULT TRUE,
    is_featured BOOLEAN NOT NULL DEFAULT FALSE,
    downloads_sum INTEGER NOT NULL DEFAULT 0,
    latest_published_at TIMESTAMP NULL,
    name VARCHAR(255) NOT NULL,
    replaces VARCHAR(255) NOT NULL,
    preview_url VARCHAR(255) NOT NULL
);

-- Create tour_passes table
CREATE TABLE IF NOT EXISTS tour_passes (
    id BIGSERIAL PRIMARY KEY,
    "shareId" VARCHAR(11) NOT NULL UNIQUE,
    cover_url VARCHAR(255) NOT NULL,
    is_public BOOLEAN NOT NULL DEFAULT TRUE,
    is_featured BOOLEAN NOT NULL DEFAULT FALSE,
    downloads_sum INTEGER NOT NULL DEFAULT 0,
    latest_published_at TIMESTAMP NULL,
    name VARCHAR(255) NOT NULL,
    artist VARCHAR(200) NULL
);

-- Create junction table for tour_passes and charts
CREATE TABLE IF NOT EXISTS tour_pass_charts (
    tour_pass_id BIGINT NOT NULL REFERENCES tour_passes(id) ON DELETE CASCADE,
    chart_id BIGINT NOT NULL REFERENCES charts(id) ON DELETE CASCADE,
    PRIMARY KEY (tour_pass_id, chart_id)
);

-- Create interactions table with at-least-one target constraint
CREATE TABLE IF NOT EXISTS interactions (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    chart_id BIGINT NULL REFERENCES charts(id) ON DELETE SET NULL,
    tour_pass_id BIGINT NULL REFERENCES tour_passes(id) ON DELETE SET NULL,
    theme_id BIGINT NULL REFERENCES themes(id) ON DELETE SET NULL,
    liked_at TIMESTAMP NULL,
    favorited_at TIMESTAMP NULL,
    CONSTRAINT ck_interactions_at_least_one_target CHECK (
        chart_id IS NOT NULL OR tour_pass_id IS NOT NULL OR theme_id IS NOT NULL
    )
);

-- Helpful indexes
CREATE INDEX IF NOT EXISTS idx_interactions_user_id ON interactions(user_id);
CREATE INDEX IF NOT EXISTS idx_interactions_chart_id ON interactions(chart_id);
CREATE INDEX IF NOT EXISTS idx_interactions_tour_pass_id ON interactions(tour_pass_id);
CREATE INDEX IF NOT EXISTS idx_interactions_theme_id ON interactions(theme_id);

-- Add aggregated columns to charts if missing
ALTER TABLE charts ADD COLUMN IF NOT EXISTS downloads_sum INTEGER NOT NULL DEFAULT 0;
ALTER TABLE charts ADD COLUMN IF NOT EXISTS latest_published_at TIMESTAMP NULL;
