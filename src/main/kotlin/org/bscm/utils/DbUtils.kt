package org.bscm.utils

import io.ktor.util.logging.*
import org.jetbrains.exposed.v1.dao.flushCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.sql.SQLRecoverableException
import java.sql.SQLTransientException

private val log = KtorSimpleLogger("DbUtils")

/**
 * Flushes Exposed's [EntityCache][org.jetbrains.exposed.v1.dao.EntityCache] so that all
 * pending DAO writes (INSERT / UPDATE) are turned into SQL and sent to the database.
 *
 * **Why this exists:** DAO writes in Exposed are buffered in the transaction's EntityCache.
 * Raw DSL queries ([Query.toList][org.jetbrains.exposed.v1.jdbc.Query]) do **not** trigger
 * an automatic flush, so reads following unflushed writes can legitimately see zero rows.
 *
 * Call this before any hand-built DSL query that must see entities created or mutated
 * earlier in the same transaction.
 *
 * Safe to call outside a transaction (no-op).
 */
fun flushEntityCache() {
    val txn = TransactionManager.currentOrNull() ?: return
    txn.flushCache()
}

/**
 * Wraps a [suspendTransaction] block with exponential-backoff retry logic for transient
 * database errors (connection resets, pool exhaustion, serialization deadlocks).
 *
 * - Retries up to [maxRetries] times (default 3).
 * - Delay starts at [initialDelayMs] and doubles each attempt.
 * - Only [SQLTransientException] and [SQLRecoverableException] (and their subclasses)
 *   trigger a retry; all other exceptions propagate immediately.
 */
suspend fun <T> withRetryOnTransientErrors(
    maxRetries: Int = 3,
    initialDelayMs: Long = 100L,
    block: suspend () -> T,
): T {
    var lastException: Exception? = null
    var delay = initialDelayMs

    repeat(maxRetries) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            if (e is SQLTransientException || e is SQLRecoverableException) {
                lastException = e
                log.warn("Transient DB error on attempt ${attempt + 1}/$maxRetries: ${e.message}. Retrying in ${delay}ms...")
                Thread.sleep(delay)
                delay *= 2
            } else {
                throw e
            }
        }
    }
    throw lastException ?: IllegalStateException("Retry loop completed without exception")
}
