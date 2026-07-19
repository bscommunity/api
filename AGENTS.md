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
| `utils/DbUtils.kt` | `flushEntityCache()` and `withRetryOnTransientErrors()` |

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

**Rule:** The content ID must be generated **before** the Discord upload. This decouples the content's identity from Discord's response and allows building Discord-facing URLs (button links, message links) upfront.

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
2. Use discordMessageId as the content ID  ← couples identity to Discord
3. Create DB rows
```

### Nano ID Generation

Use `org.bscm.utils.NanoIdUtils.generateContentId()` — generates a 10-char ID where the first character is alphanumeric only (`0-9a-zA-Z`), never `-` or `_`:

```kotlin
val contentId = NanoIdUtils.generateContentId()
```

This is also the `clientDefault` on `CatalogItemTable`, so `CatalogItemEntity.new { ... }` (without explicit ID) uses the same safe format.

### Discord Coordinate Storage

After uploading to Discord, store the message metadata on the catalog item:

```kotlin
catalogItemRepository.updateDiscordCoordinates(contentId, channelId, messageId)
```

- `discordMessageId` — the webhook message snowflake (used for edits, deletes, message links)
- `discordChannelId` — the channel where the message lives (required for Discord message link format: `https://discord.com/channels/{guildId}/{channelId}/{messageId}`)

### Deleting Discord Messages

When cleaning up content, always use `discordMessageId` (not the content ID) to delete the Discord message:

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

## Pending Improvements

1. **Structured metrics:** Replace manual `System.currentTimeMillis()` timing with Micrometer histograms (Ktor plugin available).
2. **Search index:** Add `pg_trgm` GIN indexes on `normalized_title`, `normalized_artist`, `normalized_album` for `LIKE '%term%'` queries.
3. **Cursor pagination:** Replace offset-based pagination in `fetchChartEntities` with keyset/cursor on `(sort_column, id)`.
