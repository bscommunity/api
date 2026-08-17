# Plan: Themes Implementation + Native Postgres Enum Refactor

## Overview

Two work streams delivered together:

**A. Native Postgres enum refactor** — Convert all 9 `enumerationByName` columns to native Postgres `ENUM` types. This gives DB-level type safety and is a one-time schema improvement while the project is in development.

**B. Complete Themes content type** — Add the missing pieces: `BeatstarThemeId` enum for the `replaces` column (as a native Postgres enum), `originalArtwork` metadata field, theme version upload endpoint, and move BeatstarThemes data files.

---

## Part A: Native Postgres Enum Refactor

### What changes

All 9 enums currently using `enumerationByName` (varchar storage) are converted to native Postgres enums via Exposed's `enumeration()`. The Kotlin enum classes stay the same — only the table column definitions change.

**Enum → DB type mapping:**

| Kotlin Enum | Postgres Type | Table(s) | Values |
|---|---|---|---|
| `CatalogItemType` | `catalog_item_type` | `catalog_items.type` | CHART, TOUR_PASS, THEME |
| `CatalogItemStatus` | `catalog_item_status` | `catalog_items.status` | DRAFT, PUBLISHED, ARCHIVED, REMOVED |
| `Visibility` | `visibility` | `catalog_items.visibility` | PUBLIC, UNLISTED, PRIVATE |
| `Difficulty` | `difficulty` | `charts.difficulty` | NORMAL, HARD, EXTREME |
| `StreamingPlatform` | `streaming_platform` | `track_streaming_refs.platform`, `album_streaming_refs.platform` | SPOTIFY, APPLE_MUSIC, ... (17 values) |
| `ContributorRole` | `contributor_role` | `contributors.role` | AUTHOR, CHART, AUDIO, REVISION, EFFECTS, SYNC, GAMEPLAY, ART, TEXTURES |
| `ActivityType` | `activity_type` | `user_activity.type` | CREATED_CHART, CREATED_TOUR_PASS, CREATED_THEME, LIKED_CHART, LIKED_TOUR_PASS, LIKED_THEME, BOOKMARKED_CHART, BOOKMARKED_TOUR_PASS, BOOKMARKED_THEME, FOLLOWED_USER |
| `UserRole` | `user_role` | `users.role` | USER, MODERATOR, ADMIN |
| `CollectionKind` | `collection_kind` | `collections.kind` | USER, LIKES, BOOKMARKS |

### Table file changes (9 files)

Each file changes from `enumerationByName("col", N, Enum::class)` to `enumeration("col", Enum::class)`:

| File | Line | Before | After |
|---|---|---|---|
| `CatalogItemTable.kt` | 18 | `enumerationByName("type", 20, CatalogItemType::class)` | `enumeration("type", CatalogItemType::class)` |
| `CatalogItemTable.kt` | 19 | `enumerationByName("status", 20, CatalogItemStatus::class)` | `enumeration("status", CatalogItemStatus::class)` |
| `CatalogItemTable.kt` | 20 | `enumerationByName("visibility", 20, Visibility::class)` | `enumeration("visibility", Visibility::class)` |
| `ChartTable.kt` | 14 | `enumerationByName("difficulty", 10, Difficulty::class)` | `enumeration("difficulty", Difficulty::class)` |
| `TrackStreamingRefTable.kt` | 9 | `enumerationByName("platform", 30, StreamingPlatform::class)` | `enumeration("platform", StreamingPlatform::class)` |
| `AlbumStreamingRefTable.kt` | 9 | `enumerationByName("platform", 30, StreamingPlatform::class)` | `enumeration("platform", StreamingPlatform::class)` |
| `ContributorTable.kt` | 14 | `enumerationByName("role", 30, ContributorRole::class)` | `enumeration("role", ContributorRole::class)` |
| `UserActivityTable.kt` | 13 | `enumerationByName("type", 30, ActivityType::class)` | `enumeration("type", ActivityType::class)` |
| `UserTable.kt` | 21 | `enumerationByName("role", 20, UserRole::class)` | `enumeration("role", UserRole::class)` |
| `CollectionTable.kt` | 13 | `enumerationByName("kind", 20, CollectionKind::class)` | `enumeration("kind", CollectionKind::class)` |

**Note:** `CollectionTable.kind` has `.default(CollectionKind.USER)` — `enumeration()` supports `.default()` the same way.

### No other code changes needed

- All enum values are already stored as `.name` (UPPER_CASE) by `enumerationByName`, which matches the native Postgres enum values exactly
- All queries use exact-match (`eq`), never `LIKE`
- All DAO entity property types stay the same (the Kotlin enum type doesn't change)
- All DTOs, serializers, and route handlers stay the same

---

## Part B: Complete Themes Content Type

### 1. New enum: `BeatstarThemeId`

**New file:** `src/main/kotlin/org/bscm/models/enums/BeatstarThemeId.kt`

Kotlin enum with all ~90 BeatstarTheme IDs from `BeatstarThemes.kt`. Each value uses the exact ID format (lowercase, hyphens). Exposed's `enumeration()` maps directly to the Postgres enum.

```kotlin
@Serializable
enum class BeatstarThemeId {
    BAYOU, CHROME_SKULL, FINAL_BATTLE, HADES, HAUNTED_HOUSE,
    HELP_THE_EARTH, JULY_4TH, LIQUID_CHROME, RAVEN, ROCK_CITYSCAPE,
    THOR, ULTRAVIOLET,
    // ... pop, alternative, hipHop, universal, rnb, dance, country values
    AVALON_SOUNDWAVE;

    companion object {
        /**
         * Converts from the original Beatstar theme ID format (e.g. "chrome-skull")
         * to the enum value (e.g. CHROME_SKULL).
         */
        fun fromBeatstarId(id: String): BeatstarThemeId =
            valueOf(id.uppercase().replace('-', '_'))

        /**
         * Converts back to the original Beatstar theme ID format.
         */
        fun BeatstarThemeId.toBeatstarId(): String =
            name.lowercase().replace('_', '-')
    }
}
```

**Why hyphens → underscores:** Postgres enums don't support hyphens. The enum values in the DB will be `CHROME_SKULL`, `FINAL_BATTLE`, etc. The `fromBeatstarId`/`toBeatstarId` helpers handle conversion to/from the original `chrome-skull` format used in `BeatstarThemes.kt` and client payloads.

### 2. Migration: `V1__themes_and_native_enums.sql`

**New file:** `src/main/resources/db/migration/V1__themes_and_native_enums.sql`

Creates all native enum types, converts existing varchar columns, and adds the `original_artwork` column:

```sql
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

-- ─── Theme table additions ───────────────────────────────────────────

ALTER TABLE themes ADD COLUMN original_artwork VARCHAR(512) NULL;
```

**Safety:** The existing varchar values are exactly the enum `.name` values (e.g., `'CHART'`, `'TOUR_PASS'`), so the `USING ...::type` cast works directly.

### 3. Modified: `ThemeTable.kt`

```kotlin
val replaces = enumeration("replaces", BeatstarThemeId::class)
```

### 4. Modified: `ThemeEntity.kt`

Add `originalArtwork` property:

```kotlin
var originalArtwork by ThemeTable.originalArtwork
```

### 5. Modified: `Theme.kt`

```kotlin
val replaces: BeatstarThemeId,    // was: String
val originalArtwork: String? = null,  // new
```

### 6. Modified: `ThemeRepository.kt`

- `themeEntityToTheme`: Add `originalArtwork = entity.originalArtwork`
- `createTheme`/`updateTheme`: Convert string to enum via `BeatstarThemeId.fromBeatstarId(request.replaces)`
- Remove `replaces` from search queries (confirmed: only used as static filters, not search)

### 7. Modified: `IThemeRepository.kt`

`createTheme`/`updateTheme` signatures keep `replaces: String` — the repository converts internally.

### 8. Modified: `ThemePublishService.kt`

No changes needed — passes `request.replaces` string through to repository.

### 9. New: `CreateThemeVersionRequest.kt`

**New file:** `src/main/kotlin/org/bscm/models/dto/theme/CreateThemeVersionRequest.kt`

```kotlin
@Serializable
data class CreateThemeVersionRequest(
    val id: ULong? = null,
    val bundleUrl: String,
    val fileSizeBytes: Long = 0,
    val changelog: List<String> = emptyList(),
)
```

### 10. Modified: `UploadService.kt`

Add `uploadThemeVersion()` method — similar to `uploadVersion()` but builds a theme-specific Discord embed (no track/artist/difficulty fields). Attaches the bundle file and edits the existing theme message.

### 11. Modified: `ThemeRoutes.kt`

Add `POST /themes/{id}/versions` endpoint inside `authenticate("auth-bearer")`:
- Parse multipart: version JSON + bundle file
- Compute SHA-256 bundle hash, check for duplicates
- Upload bundle to Discord (edit existing theme message via `uploadService.uploadThemeVersion()`)
- Create version via `versionRepository.addVersion()`
- Respond with `HttpStatusCode.Created` + version

### 12. Moved: `services/theme/*` → `models/theme/`

Move `BeatstarTheme.kt` and `BeatstarThemes.kt` from `services/theme/` to `models/theme/`. Update package declarations and all imports.

### 13. Modified: `Routing.kt`

Update import path for `themeRoutes` if it moves (function name stays the same).

---

## Files Summary

### Part A: Native Postgres enum refactor

| Action | File |
|--------|------|
| **New** | `src/main/resources/db/migration/V1__themes_and_native_enums.sql` |
| **Modify** | `models/tables/CatalogItemTable.kt` (3 columns) |
| **Modify** | `models/tables/ChartTable.kt` (1 column) |
| **Modify** | `models/tables/TrackStreamingRefTable.kt` (1 column) |
| **Modify** | `models/tables/AlbumStreamingRefTable.kt` (1 column) |
| **Modify** | `models/tables/ContributorTable.kt` (1 column) |
| **Modify** | `models/tables/UserActivityTable.kt` (1 column) |
| **Modify** | `models/tables/UserTable.kt` (1 column) |
| **Modify** | `models/tables/CollectionTable.kt` (1 column) |

### Part B: Themes implementation

| Action | File |
|--------|------|
| **New** | `models/enums/BeatstarThemeId.kt` |
| **New** | `models/dto/theme/CreateThemeVersionRequest.kt` |
| **Move** | `services/theme/BeatstarTheme.kt` → `models/theme/BeatstarTheme.kt` |
| **Move** | `services/theme/BeatstarThemes.kt` → `models/theme/BeatstarThemes.kt` |
| **Modify** | `models/tables/ThemeTable.kt` |
| **Modify** | `models/dao/ThemeEntity.kt` |
| **Modify** | `models/Theme.kt` |
| **Modify** | `models/interfaces/IThemeRepository.kt` |
| **Modify** | `repository/ThemeRepository.kt` |
| **Modify** | `services/UploadService.kt` |
| **Modify** | `routes/ThemeRoutes.kt` |
| **Modify** | `plugins/Routing.kt` (import path) |

### Not modified

| Enum | Reason |
|------|--------|
| `Genre` | Stored as `VARCHAR[]` array in `tracks.genres`, not a single column. Different pattern, not worth converting. |

---

## Verification

1. `./gradlew compileKotlin` — all sources compile
2. `./gradlew compileTestKotlin` — test sources compile
3. `./gradlew test` — existing tests pass (H2 in PG-compatibility mode supports `CREATE TYPE ... AS ENUM`)
4. Manual check: Theme CRUD, version upload, bundle download
