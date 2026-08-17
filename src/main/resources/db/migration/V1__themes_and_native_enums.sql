-- ─── Native Postgres enum types ──────────────────────────────────────

CREATE TYPE catalog_item_type AS ENUM ('CHART', 'TOUR_PASS', 'THEME');
CREATE TYPE catalog_item_status AS ENUM ('DRAFT', 'PUBLISHED', 'ARCHIVED', 'REMOVED');
CREATE TYPE visibility AS ENUM ('PUBLIC', 'UNLISTED', 'PRIVATE');
CREATE TYPE difficulty AS ENUM ('NORMAL', 'HARD', 'EXTREME');
CREATE TYPE streaming_platform AS ENUM (
    'SPOTIFY', 'APPLE_MUSIC', 'YOUTUBE_MUSIC', 'DEEZER', 'TIDAL',
    'AMAZON_MUSIC', 'SOUNDCLOUD', 'LAST_FM', 'PANDORA', 'NAPSTER',
    'QOBUZ', 'YANDEX_MUSIC', 'BOOMPLAY', 'ANGHAMI', 'AUDIOMACK',
    'SHAZAM', 'JIOSAAVN'
);
CREATE TYPE contributor_role AS ENUM (
    'AUTHOR', 'CHART', 'AUDIO', 'REVISION', 'EFFECTS',
    'SYNC', 'GAMEPLAY', 'ART', 'TEXTURES'
);
CREATE TYPE activity_type AS ENUM (
    'CREATED_CHART', 'CREATED_TOUR_PASS', 'CREATED_THEME',
    'LIKED_CHART', 'LIKED_TOUR_PASS', 'LIKED_THEME',
    'BOOKMARKED_CHART', 'BOOKMARKED_TOUR_PASS', 'BOOKMARKED_THEME',
    'FOLLOWED_USER'
);
CREATE TYPE user_role AS ENUM ('USER', 'MODERATOR', 'ADMIN');
CREATE TYPE collection_kind AS ENUM ('USER', 'LIKES', 'BOOKMARKS');

-- ─── Beatstar theme IDs ──────────────────────────────────────────────

CREATE TYPE beatstar_theme_id AS ENUM (
    'BAYOU', 'CHROME_SKULL', 'FINAL_BATTLE', 'HADES', 'HAUNTED_HOUSE',
    'HELP_THE_EARTH', 'JULY_4TH', 'LIQUID_CHROME', 'RAVEN', 'ROCK_CITYSCAPE',
    'THOR', 'ULTRAVIOLET',
    'BLUE_DUNES', 'EGYPTIAN_GOLD', 'FRIGHT_NIGHT', 'HEARTBEATS', 'PRIDE_2024',
    'STAINED_GLASS', 'SUMMER_SUNDOWN', 'WOMEN_S_DAY', 'BREAK_FREE', 'INK_FLOW',
    'LIQUID_CANDY', '_30_SECONDS_TO_MARS', 'TROLLS_BAND_TOGETHER', 'BAND_TOGETHER',
    'CHRISTMAS_JUMPER', 'ORBIT', 'LUNAR_NEW_YEAR_DRAGON', 'POP_CITYSCAPE',
    'HAPPY_BIRTHDAY', 'LATIN_SOUNDS', 'MOON_MUSIC', 'JOHN_LENNON', 'EASTER_EGGS',
    'LATIN_HEAT', 'MOTHER_S_DAY_2025', 'WORLD_SONG_CHAMPIONSHIP',
    'BLACK_INK', 'THANKSGIVING', 'PSYCHEDELIC', 'LIQUID_URANIUM',
    'ST_PATRICK_S_DAY', 'EGGSTRAVAGANZA', 'ALTERNATIVE_CITYSCAPE', 'THE_KILLERS',
    'DAY_OF_THE_DEAD', 'FESTIVE_CANDY', 'CELESTIAL_CATS', 'BUDDHA', 'POSEIDON',
    'FREE_THROW_FRENZY', 'DRAGON', 'GAME_TIME', 'GRAFFITI', 'LIQUID_FUEL',
    'ROYAL_PANTHER', 'OVERSPRAY', 'HIP_HOP_CITYSCAPE', 'SAPPHIRE_SHARDS',
    'YEAR_OF_THE_SNAKE', 'ICE', 'GOLDEN_WEEK', 'ANUBIS', 'BASKETBALL',
    'AMATERASU', 'MENTAL_HEALTH_AWARENESS', 'ODIN', 'RA', 'ZEUS',
    'LIQUID_GOLD', 'NEON_RADIANCE', 'ROSE_GOLD', 'PALM_SKIES', 'VALENTINES',
    'RNB_CITYSCAPE', 'CHERRY_BLOSSOM', 'GREEN_GAME_JAM_2024', '_24K_GOLD',
    'WARM_BLOSSOM', 'PEACOCK', 'JUNETEENTH',
    'FASTLANE', 'FRACTURED_SOUNDS', 'PLASMA', 'SONIC_WAVES', 'CARNAVAL',
    'DANCE_CITYSCAPE', 'AMETHYST_SHARDS', 'CRETACEOUS', 'CARNAVAL_2025',
    'DISCO_FLAMINGO', 'SONGKRAN', 'OUR_POWER_OUR_PLANET', 'NEON',
    'AVALON_SOUNDWAVE',
    'SPOTLIGHTS', 'LIQUID_AMBER', 'COUNTRY_CITYSCAPE', 'BLACK_STALLION',
    'MEMORIAL_DAY', 'FATHERS_DAY'
);

-- ─── Convert existing varchar columns to native enums ────────────────

ALTER TABLE catalog_items ALTER COLUMN "type" TYPE catalog_item_type USING "type"::catalog_item_type;
ALTER TABLE catalog_items ALTER COLUMN status TYPE catalog_item_status USING status::catalog_item_status;
ALTER TABLE catalog_items ALTER COLUMN visibility TYPE visibility USING visibility::visibility;
ALTER TABLE charts ALTER COLUMN difficulty TYPE difficulty USING difficulty::difficulty;
ALTER TABLE track_streaming_refs ALTER COLUMN platform TYPE streaming_platform USING platform::streaming_platform;
ALTER TABLE album_streaming_refs ALTER COLUMN platform TYPE streaming_platform USING platform::streaming_platform;
ALTER TABLE contributors ALTER COLUMN role TYPE contributor_role USING role::contributor_role;
ALTER TABLE user_activity ALTER COLUMN type TYPE activity_type USING type::activity_type;
ALTER TABLE users ALTER COLUMN role TYPE user_role USING role::user_role;
ALTER TABLE collections ALTER COLUMN kind TYPE collection_kind USING kind::collection_kind;

-- ─── Theme table: convert replaces to native enum ────────────────────

ALTER TABLE themes ALTER COLUMN replaces TYPE beatstar_theme_id USING replaces::beatstar_theme_id;

-- ─── Theme table: add original_artwork column ────────────────────────

ALTER TABLE themes ADD COLUMN original_artwork VARCHAR(512) NULL;
