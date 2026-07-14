package org.bscm.services.preview

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.bscm.models.dto.PreviewResponse
import org.bscm.models.enums.PreviewProvider
import org.bscm.models.interfaces.CacheRepository
import org.bscm.services.preview.resolvers.PreviewResolverRegistry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PreviewService(
    private val registry: PreviewResolverRegistry,
    private val cache: CacheRepository<PreviewResponse>,
    private val clock: Clock = Clock.System
) {

    private val safetyMargin = 60.seconds
    private val fallbackTtl = 7.days

    object PreviewCacheKey {
        fun of(provider: PreviewProvider, trackId: String): String =
            "preview:${provider.name.lowercase()}:$trackId"
    }

    suspend fun getPreview(
        provider: PreviewProvider,
        trackId: String
    ): PreviewResponse? {

        val cacheKey = PreviewCacheKey.of(provider, trackId)
        val now = clock.now()

        // 1. Cache lookup
        cache.get(cacheKey)?.let { cached ->
            if (cached.expiresAt == null || cached.expiresAt > now) {
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
                val ttlMs = (expiresAt - safetyMargin).toEpochMilliseconds() - now.toEpochMilliseconds()
                val ttl = ttlMs.milliseconds
                if (ttl.isNegative() || ttl == Duration.ZERO) null else ttl
            }
        }
}