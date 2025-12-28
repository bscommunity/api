package org.bscm.services.preview

import org.bscm.models.dto.PreviewResponse
import org.bscm.models.enums.PreviewProvider
import org.bscm.models.interfaces.CacheRepository
import org.bscm.services.preview.resolvers.PreviewResolverRegistry
import java.time.Clock
import java.time.Duration
import java.time.Instant

class PreviewService(
    private val registry: PreviewResolverRegistry,
    private val cache: CacheRepository<PreviewResponse>,
    private val clock: Clock = Clock.systemUTC()
) {

    private val safetyMargin = Duration.ofSeconds(60)
    private val fallbackTtl = Duration.ofDays(7) // stable providers (iTunes)

    object PreviewCacheKey {
        fun of(provider: PreviewProvider, trackId: String): String =
            "preview:${provider.name.lowercase()}:$trackId"
    }

    suspend fun getPreview(
        provider: PreviewProvider,
        trackId: String
    ): PreviewResponse? {

        val cacheKey = PreviewCacheKey.of(provider, trackId)
        val now = Instant.now(clock)

        // 1. Cache lookup
        cache.get(cacheKey)?.let { cached ->
            if (cached.expiresAt == null || cached.expiresAt.isAfter(now)) {
                return cached
            }
        }

        // 2. Resolve via provider
        val resolver = registry.get(provider)
        val resolved = resolver.resolve(trackId) ?: return null

        // 3. Compute TTL
        val ttl = computeTtl(resolved, now)

        // 4. Cache result (best-effort)
        ttl?.let {
            cache.put(cacheKey, resolved, it)
        }

        return resolved
    }

    private fun computeTtl(
        result: PreviewResponse,
        now: Instant
    ): Duration? =
        when (val expiresAt = result.expiresAt) {
            null -> fallbackTtl
            else -> {
                val ttl = Duration.between(
                    now,
                    expiresAt.minus(safetyMargin)
                )
                if (ttl.isNegative || ttl.isZero) null else ttl
            }
        }
}