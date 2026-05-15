package org.bscm.repository

import org.bscm.models.StreamingRef
import org.bscm.models.dao.StreamingLinkEntity
import org.bscm.utils.StreamingPlatformUtils
import org.jetbrains.exposed.sql.insertIgnore
import java.util.*

internal object StreamingLinkRepository {
    data class ResolvedLinks(
        val ids: List<UUID>,
        val entities: List<StreamingLinkEntity>,
    )

    /**
     * Resolves existing streaming links by URL and creates missing ones.
     * URLs are globally unique in the current schema, so URL is the stable lookup key.
     */
    fun resolveOrCreateLinks(links: List<StreamingRef>): ResolvedLinks {
        val deduped = links
            // Persist a stable canonical URL to avoid duplicates caused by tracking params/whitespace.
            .map { StreamingRef(platform = it.platform, url = StreamingPlatformUtils.normalizeUrl(it.url)) }
            .filter { it.url.isNotBlank() }
            .distinctBy { it.url }

        if (deduped.isEmpty()) return ResolvedLinks(emptyList(), emptyList())

        val urls = deduped.map { it.url }

        val existingByUrl = StreamingLinkEntity
            .find { StreamingLinkTable.url inList urls }
            .associateBy { it.url }

        // Create missing links in a race-safe way. Since `url` is UNIQUE, two concurrent
        // transactions could both decide a URL is missing; `insertIgnore` turns that into
        // an INSERT-or-NOP and we re-read afterwards.
        val missing = deduped.filter { it.url !in existingByUrl }
        if (missing.isNotEmpty()) {
            missing.forEach { link ->
                StreamingLinkTable.insertIgnore {
                    it[id] = UUID.randomUUID()
                    it[platform] = link.platform
                    it[url] = link.url
                }
            }
        }

        // Re-fetch all resolved entities (existing + newly inserted) and keep stable input order.
        val resolvedByUrl = StreamingLinkEntity
            .find { StreamingLinkTable.url inList urls }
            .associateBy { it.url }

        val resolvedEntities = deduped.mapNotNull { link -> resolvedByUrl[link.url] }

        return ResolvedLinks(
            ids = resolvedEntities.map { it.id.value },
            entities = resolvedEntities,
        )
    }
}


