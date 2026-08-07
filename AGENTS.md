# AGENTS.md — API Codebase Conventions

## Database & Exposed ORM

### EntityCache Flush Contract

**Rule:** Any code that builds a raw DSL query (`Query.toList()`, `query.map {}`, etc.) after DAO writes (`Entity.new {}`, property assignments) in the **same transaction** must call `flushEntityCache()` before the query.

```kotlin
// Correct: flush before raw read
flushEntityCache()
val result = ChartTable.selectAll().where { ChartTable.id eq id }.toList()

// Wrong: raw read without flush (may miss unflushed DAO writes)
val result = ChartTable.selectAll().where { ChartTable.id eq id }.toList()
```

**Why:** Exposed's `EntityCache` buffers DAO writes (INSERTs/UPDATEs) and only sends them to the database when flushed. Raw DSL queries go directly to JDBC and bypass the cache. DAO reads (`Entity.find`, `Entity[id]`) auto-flush, but raw queries do not.

**Reference utility:** `org.bscm.utils.flushEntityCache()` — safe no-op outside a transaction.

### Transaction Boundaries

- **Repositories** own `suspendTransaction {}` blocks (transaction boundary).
- **Repositories without their own transaction** (e.g., `TrackRepository`, `CatalogItemRepository`) must be called within the caller's transaction scope.
- Do NOT nest `suspendTransaction {}` blocks unless explicitly needed for savepoints.

### Retry/Transient Error Handling

Use `org.bscm.utils.withRetryOnTransientErrors { }` to wrap transactions that may encounter transient connection errors (pool exhaustion, serialization deadlocks). Exponential backoff with configurable max retries.

### Key Files

| File | Role |
|------|------|
| `repository/ChartRepository.kt` | Orchestrator — owns transactions, calls other repos |
| `repository/TrackRepository.kt` | Transaction-less — must be called within existing txn |
| `repository/CatalogItemRepository.kt` | Transaction-less — must be called within existing txn |
| `repository/ChartQueryBuilder.kt` | Builds raw DSL queries (joins, filters, search) |
| `repository/ChartResultAssembler.kt` | Maps `ResultRow` lists to domain models |
| `repository/TourPassRepository.kt` | Tour pass queries — own transaction, SQL-level filtering |
| `repository/ThemeRepository.kt` | Theme queries — own transaction, SQL-level filtering |
| `repository/ContributorRepository.kt` | Contributor CRUD — own transaction |
| `utils/DbUtils.kt` | `flushEntityCache()` and `withRetryOnTransientErrors()` |
| `utils/UserStatsUtils.kt` | `fetchUserStats()` — batched like/bookmark lookup, must be called within txn |

### Flush API (Exposed 1.0.0 / v1 namespace)

```kotlin
import org.jetbrains.exposed.v1.dao.flushCache   // extension on Transaction
import org.jetbrains.exposed.v1.dao.entityCache   // extension property on Transaction

// Option A: convenience extension (preferred)
TransactionManager.current().flushCache()

// Option B: direct EntityCache access
TransactionManager.current().entityCache.flush()
```

## Content Identity & Discord Coordinates

### Shared ID Pattern

Every catalog subtype (`chart`, `tour_pass`, `theme`) shares the **same ID** as its parent `catalog_items` row. The `CatalogItemEntity` resolves subtypes via `XxxEntity.findById(id)`.

**Rule:** The catalog ID must be generated **before** the Discord upload. This decouples the content's identity from Discord's response and allows building Discord-facing URLs (button links, message links) upfront.

**Correct flow (all content types):**
```
1. Generate Nano ID (client-side)
2. Build Discord payload using the ID (e.g., app link button URL)
3. Upload to Discord → get discordMessageId + discordChannelId
4. Create DB rows (catalog_items + subtype) with the Nano ID
5. Store Discord coordinates via updateDiscordCoordinates(id, channelId, messageId)
```

**Wrong flow (never do this):**
```
1. Upload to Discord → get discordMessageId
2. Use discordMessageId as the catalog ID  ← couples identity to Discord
3. Create DB rows
```

### Nano ID Generation

Use `org.bscm.utils.NanoIdUtils.generateCatalogId()` — generates a 10-char ID where the first character is alphanumeric only (`0-9a-zA-Z`), never `-` or `_`:

```kotlin
val catalogId = NanoIdUtils.generateCatalogId()
```

This is also the `clientDefault` on `CatalogItemTable`, so `CatalogItemEntity.new { ... }` (without explicit ID) uses the same safe format.

### Discord Coordinate Storage

After uploading to Discord, store the message metadata on the catalog item:

```kotlin
catalogItemRepository.updateDiscordCoordinates(catalogId, channelId, messageId)
```

- `discordMessageId` — the webhook message snowflake (used for edits, deletes, message links)
- `discordChannelId` — the channel where the message lives (required for Discord message link format: `https://discord.com/channels/{guildId}/{channelId}/{messageId}`)

### Deleting Discord Messages

When cleaning up content, always use `discordMessageId` (not the catalog ID) to delete the Discord message:

```kotlin
tourPass.discordMessageId?.let { messageId ->
    runCatching { uploadService.deleteMessage(messageId) }
}
```

## Testing

- Tests use JUnit 4 (`@Test`, `@Before`, `@After`)
- Integration tests use H2 in PostgreSQL-compatibility mode with Flyway migrations
- Run: `./gradlew test --tests "org.bscm.<TestClass>"`

## Build & Lint

```bash
./gradlew compileKotlin          # compile main sources
./gradlew compileTestKotlin      # compile test sources
./gradlew test                   # run all tests
```

## Repository Query Conventions

### SQL-Level Filtering (No In-Memory Filtering)

Always use SQL `WHERE` clauses (via `selectAll().where {}`, `Entity.find {}`, etc.) for filtering in repository methods. Never load all entities and filter in Kotlin (`Entity.all().filter {}`).

```kotlin
// Correct: SQL-level filtering
val query = TourPassTable.selectAll()
query.where { TourPassTable.id inList ids }
val results = query.limit(pageSize).offset(offset).toList()

// Wrong: loads everything into memory
val results = TourPassEntity.all().toList().filter { it.id.value in ids }
```

### Count Queries — Single Grouped Query

For counting items by type, prefer a single grouped `COUNT` query over multiple separate queries:

```kotlin
// Correct: single grouped query
val countColumn = CatalogItemTable.id.count()
val rows = CatalogItemTable
    .select(CatalogItemTable.type, countColumn)
    .where { CatalogItemTable.authorId eq userId }
    .groupBy(CatalogItemTable.type)
    .associate { it[CatalogItemTable.type] to it[countColumn].toInt() }

// Wrong: three separate COUNT queries
val charts = CatalogItemTable.innerJoin(...).select(...).where(...).count()
val tourPasses = CatalogItemTable.innerJoin(...).select(...).where(...).count()
val themes = CatalogItemTable.innerJoin(...).select(...).where(...).count()
```

### N+1 Prevention

When returning a list of items, batch-fetch user stats (likes/bookmarks timestamps) once rather than N times:

```kotlin
// Correct: batch fetch
val userStats = if (userId != null) {
    UserStatsUtils.fetchUserStats(userId, ids)
} else emptyMap()
items.map { item -> buildItem(item, likedAt = userStats[item.id]?.first) }

// Wrong: N+1 per entity
items.map { item -> UserStatsUtils.fetchUserStats(userId, listOf(item.id)) }
```

For follower/following N+1: use `UserEntity.wrapRow(row)` on the already-joined row instead of `UserEntity.findById()` per row.

### Route Path Conventions

| Old Path | New Path | Reason |
|----------|----------|--------|
| `/contributors/chart/{id}` | `/contributors/{catalogItemId}` | Generalize from chart-only |
| `/versions/chart/{...}` | `/versions/{...}` | Versions are for all versionable items |
| `/charts/{id}/issues` | `/changelog/{catalogItemId}/issues` | |

### Route Error Style

Use `throw BadRequestException("message")` instead of `call.respond(HttpStatusCode.BadRequest, "message")`. The exception is handled by Ktor's error handler which produces a consistent error response.

### NoContent Responses

Use `call.respond(HttpStatusCode.NoContent)` (without body) instead of `call.respond(HttpStatusCode.NoContent, true)`. A 204 response must not have a body.

## Versioning Convention

Version-related counts (`versionsCount`) and latest-version lookups are **always computed at read time** via `VersionTable`, never cached on `CatalogItemTable` or any other table.

- **Count:** `VersionTable.selectAll().where { catalogItemId eq itemId }.count()`
- **Latest version:** `VersionTable.selectAll().where { catalogItemId eq itemId }.orderBy(versionCode DESC).limit(1).firstOrNull()`
- **Batch:** One grouped count query + one grouped latest-version query per page; see `ChartRepository.enrichWithVersionData()` for the pattern.
- **`bundleHash`** lives on `VersionTable` (uniquely constrained globally), not on the catalog item.
- **Write path:** `VersionRepository.addVersion` / `removeVersion` only touch `VersionTable` rows; they no longer update cached columns on `CatalogItemTable`.

TourPass items are never versionable and must never touch `VersionTable`.

## Pending Improvements

1. **Structured metrics:** Replace manual `System.currentTimeMillis()` timing with Micrometer histograms (Ktor plugin available).
2. **Search index:** Add `pg_trgm` GIN indexes on `normalized_title`, `normalized_artist`, `normalized_album` for `LIKE '%term%'` queries.
3. **Cursor pagination:** Replace offset-based pagination in `fetchChartEntities` with keyset/cursor on `(sort_column, id)`.
