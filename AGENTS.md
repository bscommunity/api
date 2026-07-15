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
