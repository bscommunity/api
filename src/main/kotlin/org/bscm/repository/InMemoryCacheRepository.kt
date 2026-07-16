package org.bscm.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bscm.models.interfaces.CacheRepository
import kotlin.time.Duration

class InMemoryCacheRepository<T> : CacheRepository<T> {

    private data class Entry<T>(val value: T, val expiresAt: Long)

    private val cache = HashMap<String, Entry<T>>()
    private val mutex = Mutex()

    override suspend fun get(key: String): T? = mutex.withLock {
        val entry = cache[key] ?: return@withLock null
        if (System.currentTimeMillis() > entry.expiresAt) {
            cache.remove(key)
            null
        } else {
            entry.value
        }
    }

    override suspend fun put(key: String, value: T, ttl: Duration) = mutex.withLock {
        cache[key] = Entry(value, System.currentTimeMillis() + ttl.inWholeMilliseconds)
    }

    override suspend fun evict(key: String) = mutex.withLock {
        cache.remove(key)
        Unit
    }
}
