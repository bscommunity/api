package org.bscm.repository

import io.lettuce.core.RedisClient
import kotlinx.coroutines.future.await
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.bscm.models.interfaces.CacheRepository
import java.time.Duration

class RedisCacheRepository<T>(
    redisClient: RedisClient,
    private val json: Json,
    private val serializer: KSerializer<T>
) : CacheRepository<T> {

    private val connection = redisClient.connect()
    private val redis = connection.async()

    override suspend fun get(key: String): T? {
        val value = redis.get(key).await() ?: return null
        return json.decodeFromString(serializer, value)
    }

    override suspend fun put(
        key: String,
        value: T,
        ttl: Duration
    ) {
        val payload = json.encodeToString(serializer, value)

        if (!ttl.isZero && !ttl.isNegative) {
            redis.setex(key, ttl.seconds, payload)
        }
    }

    override suspend fun evict(key: String) {
        redis.del(key)
    }
}