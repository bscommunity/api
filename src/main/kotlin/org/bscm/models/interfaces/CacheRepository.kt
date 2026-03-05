package org.bscm.models.interfaces

import java.time.Duration

interface CacheRepository<T> {
    suspend fun get(key: String): T?

    suspend fun put(
        key: String,
        value: T,
        ttl: Duration
    )

    suspend fun evict(key: String)
}
